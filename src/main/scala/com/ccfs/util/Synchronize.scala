package com.ccfs.util

import java.util.concurrent.locks.{Condition, Lock, ReentrantLock}


// Simple synchronization to avoid running out of resources.
class Synchronize(lock: Lock, cond: Condition) {
  def pause() = {
    lock.lock()
    try {
      cond.await()
    } finally {
      lock.unlock()
    }
  }

  def continue() = {
    lock.lock()
    try {
      cond.signal()
    } finally {
      lock.unlock()
    }
  }
}

object Synchronize {
  def apply() = {
    val lock = new ReentrantLock()
    new Synchronize(lock, lock.newCondition())
  }
}
