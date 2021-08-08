package c4

import org.scalameter.api._
import PerformanceUtils._
import c4.ShortFuse.Ops

/** Measures the speed up by fusing steps together to avoid intermediate collections.
 * See experiment 8 for the addition of parallelism.
 * Compare this to experiment 5.
 */
class Exp07_ShortFuse extends Bench.LocalTime {

  performance of "ShortFuse" in {

    measure method "map" in {
      using(arrays) in {
        a => a.shortFuse.map(_ * 2).boom
      }
    }

    measure method "map.filter" in {
      using(arrays) in {
        a => a.shortFuse.map(_ * 2).filter(_ % 6 != 0).boom
      }
    }

    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a.shortFuse.map(_ * 2).filter(_ % 6 != 0).map(_ * 3).filter(_ > 300).boom
      }
    }

  }
}
