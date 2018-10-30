package com

import java.io.{File, FilenameFilter}

import scala.language.implicitConversions

package object ccfs {

  implicit def toFilenameFilter(f: String => Boolean): FilenameFilter = {
    new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = f(name)
    }
  }

}
