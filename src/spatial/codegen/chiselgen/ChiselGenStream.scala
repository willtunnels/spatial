package spatial.codegen.chiselgen

import argon._
import spatial.lang._
import spatial.node._
import spatial.metadata.access._
import spatial.metadata.memory._
import spatial.metadata.control._
import spatial.metadata.retiming._

trait ChiselGenStream extends ChiselGenCommon {
  var streamIns: List[Sym[Reg[_]]] = List()
  var streamOuts: List[Sym[Reg[_]]] = List()

  override protected def gen(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StreamInNew(bus) =>
      // val ens = lhs.readers.head match {case Op(StreamInBankedRead(_, ens)) => ens.length; case _ => 0} // Assume same par for all writers
      // Emit code for streams that are unrelated to DRAM nodes
      bus match {
        case CXPPixelBus => forceEmit(src"val $lhs = top.io.asInstanceOf[CXPAccelInterface].AXIS_IN")
        case BlackBoxBus(name) => blackBoxStreamInWidth = bus.nbits; forceEmit(src"val $lhs = top.io.asInstanceOf[BlackBoxStreamInterface].STREAM_IN")
        case _ =>
      }

    case StreamOutNew(bus) =>
      // val ens = lhs.writers.head match {case Op(StreamOutBankedWrite(_, data, _)) => data.size; case _ => 0} // Assume same par for all writers
      // Emit code for streams that are unrelated to DRAM nodes
      bus match {
        case CXPPixelBus => forceEmit(src"val ${lhs} = top.io.asInstanceOf[CXPAccelInterface].AXIS_OUT")
        case BlackBoxBus(name) => blackBoxStreamOutWidth = bus.nbits; forceEmit(src"val $lhs = top.io.asInstanceOf[BlackBoxStreamInterface].STREAM_OUT")
        case _ =>
      }

    case StreamOutBankedWrite(stream, data, ens) =>
      val muxPort = lhs.port.muxPort
      val base = stream.writers.filter(_.port.muxPort < muxPort).map(_.accessWidth).sum
      val parent = lhs.parent.s.get
      val sfx = if (parent.isBranch) "_obj" else ""
      val maskingLogic = src"$backpressure" 
      ens.zipWithIndex.foreach{case(e,i) =>
        val en = if (e.isEmpty) "true.B" else src"${e.toList.map(quote).mkString("&")}"
        emit(src"""${stream}.valid := ${DL(src"$datapathEn & $iiDone", src"${lhs.fullDelay}.toInt", true)} & $en & $maskingLogic""")
      }
      val Op(StreamOutNew(bus)) = stream
    
      bus match {
        case BurstCmdBus => 
          val (addrMSB, addrLSB)  = getField(stream.tp.typeArgs.head, "offset")
          val (sizeMSB, sizeLSB)  = getField(stream.tp.typeArgs.head, "size")
          emit(src"$stream.bits.addr := $data($addrMSB,$addrLSB)")
          emit(src"$stream.bits.size := $data($sizeMSB,$sizeLSB)")

        case _: BurstFullDataBus[_] => 
          val (dataMSB, dataLSB) = getField(stream.tp.typeArgs.head, "_1")
          val (strbMSB, strbLSB) = getField(stream.tp.typeArgs.head, "_2")

          if (ens.size == 1) {
            emit(src"$stream.bits.wdata(0) := $data($dataMSB,$dataLSB)")
            emit(src"$stream.bits.wstrb := $data($strbMSB,$strbLSB)")            
          } else {
            data.zipWithIndex.foreach{case (d,i) => 
              emit(src"$stream.bits.wdata($i) := $d($dataMSB,$dataLSB)")
            }
              
            emit(src"$stream.bits.wstrb := List($data).map{_($strbMSB,$strbLSB)}.reduce(Cat(_,_))")
          }

        case GatherAddrBus => 
          data.zipWithIndex.foreach{case (d,i) => 
            emit(src"$stream.bits.addr($i) := $d.r")
          }
          
        
        case _: ScatterCmdBus[_] => 
          val (dataMSB, dataLSB)  = getField(stream.tp.typeArgs.head, "_1")
          val (addrMSB, addrLSB)  = getField(stream.tp.typeArgs.head, "_2")
          data.zipWithIndex.foreach{case (d,i) => 
            emit(src"$stream.bits.addr.addr($i) := $d($addrMSB, $addrLSB)")
            emit(src"$stream.bits.wdata($i) := $d($dataMSB, $dataLSB)")
          }


        case CXPPixelBus =>
          data.zipWithIndex.foreach{case(d,i) =>
            emit(src"""${stream}.bits.TDATA.r := $d.r""")
            emit(src"""${stream}.bits.TUSER.r := 0.U //FIXME""")
          }

        case _ =>
          data.zipWithIndex.foreach{case(d,i) =>
            emit(src"""${stream}.bits := ${d}.r""")
          }
      }


    case StreamInBankedRead(strm, ens) =>
      val muxPort = lhs.port.muxPort
      val base = strm.readers.filter(_.port.muxPort < muxPort).map(_.accessWidth).sum
      val parent = lhs.parent.s.get
      val sfx = if (parent.isBranch) "_obj" else ""
      emit(createWire(quote(lhs),remap(lhs.tp)))
      emit(src"""${strm}.ready := ${and(ens.flatten.toSet)} & ($datapathEn) """)
      val Op(StreamInNew(bus)) = strm
      bus match {
        case _: BurstDataBus[_] => emit(src"""(0 until ${ens.length}).map{ i => ${lhs}(i).r := ${strm}.bits.rdata(i).r }""")
        case _: GatherDataBus[_] => emit(src"(0 until ${ens.length}).map{ i => ${lhs}(i).r := ${strm}.bits(i).r }")
        // case ScatterAckBus => emit(src"""(0 until ${ens.length}).map{ i => ${lhs}(i) := ${strm}.bits }""")
        // case BurstAckBus => emit(src"""(0 until ${ens.length}).map{ i => ${lhs}(i) := ${strm}.bits }""")
        case CXPPixelBus => emit(src"""(0 until ${ens.length}).map{ i => ${lhs}(i).r := ${strm}.bits.TDATA.r }""")
        case _ => emit(src"""(0 until ${ens.length}).map{ i => ${lhs}(i) := ${strm}.bits }""")

      }
      

    case _ => super.gen(lhs, rhs)
  }

  override def emitPostMain(): Unit = {
    val insList = List.fill(streamIns.length){ "StreamParInfo(32, 1)" }.mkString(",")
    val outsList = List.fill(streamOuts.length){ "StreamParInfo(32, 1)" }.mkString(",")

    inGen(out, s"AccelWrapper.$ext") {
      emit(src"// Non-memory Streams")
      emit(s"""val io_streamInsInfo = List(${insList})""")
      emit(s"""val io_streamOutsInfo = List(${outsList})""")
    }

    inGen(out, "Instantiator.scala") {
      emit(src"// Non-memory Streams")
      emit(s"""val streamInsInfo = List(${insList})""")
      emit(s"""val streamOutsInfo = List(${outsList})""")
    }
    super.emitPostMain()
  }
}
