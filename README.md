# Overview

Scripts used to investigate optimizations for typical array processing in scala.

It's an excuse to play around with scalameter and test out theoretical optimizations.

Typical use case is speeding up code like:

```scala
val myBigArray: Array[Int] = ...

myBigArray.map(_ * 2).filter(_ % 6 != 0)
```

# Presentation

Results were presented to the LeadIQ team as an internal presentation on Aug 12th 2021 (TODO - post link).

See [slides](slides.md) for more details.

# Methodology

There are 13 experiments (see the tests folder) each using scalameter.

Each experiment tends to focus on just or one or two changes in isolation and compare it to experiment 1
which is the "standard" approach using the scala standard library.

# Machines

They were run on 3 different machines:

- doorstop - my 2015 work issued macbook pro
- beast - an ec2 instance our team uses internally for big analytics workloads
- robox - my 2015 linux desktop machine

Details about the machines are in the slides.

# Reproducing the results

The experiments are just tests so you can run them using sbt's standard test running tricks. 

To run say experiment 3, do:

```bash
$ sbt "testOnly *Exp03*"
```

I would start an independent sbt session for each (ie. not use the sbt shell),
because I noticed that results seemed to get slower in a persistent session.

Using a new sbt session just eliminates another possible variable.

# My Results

Each experiment tested different things so it's hard to combine the results into a single view.

One common test across all experiments though was the time to compute the typical use case using different approaches:

```scala
val myBigArray: Array[Int] = ...

myBigArray.map(_ * 2).filter(_ % 6 != 0)
```

Below are time times in milliseconds on each machine for the above with an array size 1M:

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
| 2   | pred pushdown     | 70       | 26    | 22    |
|     | collect           | 41       | 19    | 16    |
------------------------------------------------------
| 3   | cfor loop         | 61       | 53    | 43    |
------------------------------------------------------
| 4   | cfor par (32)     |          |  7    | 14    |
|     | cfor par (16)     |          |  7    | 14    |
|     | cfor par (8)      | 16       |  8    | 16    |
|     | cfor par (6)      |          | 10    | 16    |
|     | cfor par (4)      | 22       | 14    | 19    |
|     | cfor par (2)      |          | 27    | 25    |
------------------------------------------------------
| 5   | fuse              | 66       | 55    | 48    |
------------------------------------------------------
| 6   | fuse par (4)      | 24       | 15    | 21    |
------------------------------------------------------
| 7   | shortfuse         | 64       | 56    | 51    |
------------------------------------------------------
| 8   | shortfuse par (4) | 23       | 14    | 20    |
------------------------------------------------------
| 9   | ByKey             | 100      | 83    | 73    |
------------------------------------------------------
| 10  | ByKey par (4)     | 39       | 21    | 26    |
------------------------------------------------------
| 11  | ByKeyConc par (4) | 35       | 66    | 47    |
------------------------------------------------------
| 12  | FragArray par (4) | 28       | 20    | 17    |
------------------------------------------------------
| 13  | stdlib .par       | 22       | 9     | 10    |
------------------------------------------------------
| 14  | ByKeyWarmup (4)   | 24       | TODO  | 25    |
------------------------------------------------------
```

# Notes

Some thoughts after running the experiments

### Grain of salt

Sometimes the numbers produced would vary wildly for the same experiment on the same machine.

It reinforced that there's a lot of random factors you can't really control and it's important to be
careful drawing conclusions from metrics.

### Workload vs Array length

Most of the optimizations scale with the length of the array,
but are independent of the work being done on each step in a `map/filter`.

e.g. iterating an array using a tight loop rather than a scala iterator, or avoiding intermediate collections

For a heavy workload this will make the improvements seem less significant.

A lot of the time we're writing code with many little steps and we like to keep them separate for readability:

```scala
array
  .map(_.toLong)
  .map(_ * 2)
  .filter(_ > 10)
  .filter(_ % 2 == 0)
```

For that style, length based optimizations become a lot more significant.

### Hyper threading?

Beast has 8 physical cores (16 "threads") and robox has 4 physical cores (8 "threads").

For experiment 4, I tested the speedup at different degrees of parallelization (p) and found that
once the level of parallelization gets past the number of physical cores,
you don't get much speedup from these extra "threads".

For example on beast, the speed for experiment 4 would half when going from p=2 to p=4,
and roughly halve again from p=4 to p=8.
But from p=8 to p=16 the speedup was so small it could be explained away as randomness.

On robox, it was a little better but still not what I'd consider "parallel".

### Slow-ish beast

I had always assumed the beast was a big beefy machine,
but on non-parallel CPU intensive tasks my old desktop tended to outperform it.

### Parallel collections

The built in parallel scala collections were pretty hard to beat (Exp13).

I was able to squeek out a couple of extra ms on beast for experiment 4 using a `cfor` based iteration.

The details for how the parallel collections are fairly abstracted away.
This makes for a nice simple api where you don't have to specify execution resources,
but it means we can't really extend it to combine it with other tricks like fusing stages,
and you can't really reason about what it will do.

For our analytics team, we could get a really fast simple library if we:

- followed the pattern of parallelization used in the standard library
- used `cfor` based iteration internally
- used fusing to avoid intermediate collections

### Spark

Predicate pushdown, fusing and caching are tricks inspired by spark.

Going too far in that direction with our own library might be reinventing the wheel.

Spark is complex and opaque though, and it's easy to fall into performance traps related to shuffles.

If the data is under say 20GB, then we could probably process it in a single JVM without shuffling related issues.
(Need to be careful of intermediate collections)

We can use spark's great api to load and deserialize data with all its projection/predicate pushdown optimizations,
to load the subset of data we need,
then collect data back into an `Array` in the driver and process it there in a simpler more optimized way.

See also comments about spark for "medium" sized data from the
[flare team](https://www.usenix.org/conference/osdi18/presentation/essertel).
