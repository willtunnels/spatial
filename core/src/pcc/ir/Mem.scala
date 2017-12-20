package pcc
package ir

/** Symbols **/
abstract class Mem[A,C[_<:A]](eid: Int)(implicit ev: C[A] <:< Mem[A,C]) extends Sym[C[A]](eid) {
  final override def isPrimitive: Boolean = false
}

/**
  * A (optionally stateful) black box
  */
abstract class Box[A](eid: Int)(implicit ev: A <:< Box[A]) extends Sym[A](eid) {
  final override def isPrimitive: Boolean = false
}

/** Nodes **/
abstract class MemAlloc[A,C[_]<:Mem[_,C]](implicit tM: Sym[C[A]]) extends Op[C[A]]
abstract class OnchipAlloc[A,C[_]<:Mem[_,C]](implicit tM: Sym[C[A]]) extends MemAlloc[A,C]
abstract class OffchipAlloc[A,C[_]<:Mem[_,C]](implicit tM: Sym[C[A]]) extends MemAlloc[A,C]

abstract class BoxAlloc[A](implicit tA: Sym[A]) extends Op[A]