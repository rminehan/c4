package c4

import spire.implicits.cfor

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

object C4Array {

  implicit class Ops[A: ClassTag](array: Array[A]) {
    def mapC4[B: ClassTag](f: A => B): Array[B] = {
      val length = array.length
      val mapped = Array.ofDim[B](length)
      cfor(0)(_ < length, _ + 1) { i =>
        mapped(i) = f(array(i))
      }
      mapped
    }

    def filterC4(pred: A => Boolean): Array[A] = {
      val keep: ArrayBuffer[A] = ArrayBuffer.empty[A]
      val length = array.length
      cfor(0)(_ < length, _ + 1) { i =>
        val element = array(i)
        if (pred(element))
          keep.append(element)
      }
      keep.toArray
    }
  }
}
