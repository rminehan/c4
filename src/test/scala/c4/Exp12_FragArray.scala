package c4

import org.scalameter.api._
import PerformanceUtils._
import c4.C4ParUtils.C4ExecutionContext
import c4.FragArray.Ops

/** Compares the speed ups from using a FragArray to speed up filtering steps
 *
 * Main point of comparison is between this and Exp4 where filtering has a final
 * flatMap(flattenBuffers) step that passes back over the data.
 */
class Exp12_FragArray extends Bench.LocalTime {

  // TODO - tune this based on your machine
  implicit val ec: C4ExecutionContext = C4ExecutionContext(parallelism = 4)

  performance of "FragArray" in {

    // The usual map(_ * 2) test doesn't interact with the FragArray so it's not here
    // ...

    // This test only uses filtering on the last step,
    // but changing it too much would mean we couldn't compare it meaningfully with Exp1 and Exp4
    measure method "map.filter" in {
      using(arrays) in {
        a => a.map(_ * 2).filterFrag(_ % 6 != 0)
      }
    }

    // For comparison with Exp1 and Exp4
    measure method "map.filter.map.filter" in {
      using(arrays) in {
        a => a
          .map(_ * 2)
          .filterFrag(_ % 6 != 0)
          .map(_ * 3)
          .filterFrag(_ > 300)
      }
    }

    // For comparison with below
    measure method "(normal) filter.map.filter.filter.map" in {
      using(arrays) in {
        a => a
          .filter(_ > 10)
          .map(_ * 2)
          .filter(_ % 4 == 0)
          .filter(_ % 3 == 0)
          .map(_ / 2)
      }
    }

    // For comparison with above
    measure method "(frag) filter.map.filter.filter.map" in {
      using(arrays) in {
        a => a
          .filterFrag(_ > 10)
          .map(_ * 2)
          .filterFrag(_ % 4 == 0)
          .filter(_ % 3 == 0)
          .map(_ / 2)
      }
    }

  }

}