package pcc
package data

import forge._
import pcc.traversal.Transformer

/** Shortcuts for metadata **/
@data object metadata {
  def apply[M<:Metadata[M]:Manifest](edge: Sym[_]): Option[M] = state.metadata[M](edge)
  def add[M<:Metadata[M]:Manifest](edge: Sym[_], m: M): Unit = state.metadata.add[M](edge, m)
  def all(edge: Sym[_]): Map[Class[_],Metadata[_]] = state.metadata.all(edge)
  def clear[M<:Metadata[M]:Manifest]: Unit = state.metadata.clear[M]
}

@data object globals {
  def add[M<:Metadata[M]:Manifest](m: M): Unit = state.globals.add[M](m)
  def get[M<:Metadata[M]:Manifest]: Option[M] = state.globals.get[M]
  def apply[M<:Metadata[M]:Manifest]: M = get[M].get
  def clear[M<:Metadata[M]:Manifest]: Unit = state.globals.clear[M]
  def mirrorAll(f: Transformer): Unit = state.globals.mirrorAll(f)
}
