---
author: Rohan
title: Microbenchmarking array optimizations
date: 2021-08-12
---

```
 __  __ _                      
|  \/  (_) ___ _ __ ___        
| |\/| | |/ __| '__/ _ \ _____ 
| |  | | | (__| | | (_) |_____|
|_|  |_|_|\___|_|  \___/       
                               
 _                     _                          _    _             
| |__   ___ _ __   ___| |__  _ __ ___   __ _ _ __| | _(_)_ __   __ _ 
| '_ \ / _ \ '_ \ / __| '_ \| '_ ` _ \ / _` | '__| |/ / | '_ \ / _` |
| |_) |  __/ | | | (__| | | | | | | | | (_| | |  |   <| | | | | (_| |
|_.__/ \___|_| |_|\___|_| |_|_| |_| |_|\__,_|_|  |_|\_\_|_| |_|\__, |
                                                               |___/ 
  __ _ _ __ _ __ __ _ _   _ 
 / _` | '__| '__/ _` | | | |
| (_| | |  | | | (_| | |_| |
 \__,_|_|  |_|  \__,_|\__, |
                      |___/ 
             _   _           _          _   _                 
  ___  _ __ | |_(_)_ __ ___ (_)______ _| |_(_) ___  _ __  ___ 
 / _ \| '_ \| __| | '_ ` _ \| |_  / _` | __| |/ _ \| '_ \/ __|
| (_) | |_) | |_| | | | | | | |/ / (_| | |_| | (_) | | | \__ \
 \___/| .__/ \__|_|_| |_| |_|_/___\__,_|\__|_|\___/|_| |_|___/
      |_|                                                     
```

---

# What's today about?

Playing around with ways to make this kind of thing faster:

```scala
val array: Array[Int] = ...

array.map(_ * 2).filter(_ % 6 != 0)
```

---

# Why?

- wanted to show the juniors micro-benchmarking with scalameter


- wanted to understand the speedup from the `cfor` loop in spire


- (analytics) processing in memory might be faster than spark for small-medium scale

---

# Agenda

- scalameter


- experiments


- observations

---

# Benefits

- learning about scalameter


- building some intuition around performance


- learn some traps around performance analysis


- _maybe_ use some of this in our analytics

---

# And don't be a wet blanket

> Premature optimization rah rah rah...

This is more for fun and research

---

# Repo

Code, slides, results and notes in the [github repo](https://github.com/rminehan/c4)

---

```
 ____            _                      _            
/ ___|  ___ __ _| | __ _ _ __ ___   ___| |_ ___ _ __ 
\___ \ / __/ _` | |/ _` | '_ ` _ \ / _ \ __/ _ \ '__|
 ___) | (_| (_| | | (_| | | | | | |  __/ ||  __/ |   
|____/ \___\__,_|_|\__,_|_| |_| |_|\___|\__\___|_|   
                                                     
```

Microbenchmarking framework

To the docs!

---

# How to use it

- modify sbt (in particular turning off parallel testing)


- don't mix performance tests with regular tests

---

# Their docs

Explains to take performance numbers with a grain of salt

Can't control all the things that dictate performance:

```scala
def time(inputs)(implicit hardware, availableResources, jvmImplementation, jvmInternalQuirks, gcCycles)
```

---

# Local vs Prod

Many factors might be different:

- jvm version


- hardware


- available resources

---

# We can still learn things

We can compare the performance of two approaches in our local system

If it makes sense, extrapolate that performance improvement to prod

---

# Overall impresions of scalameter

- docs are good


- pretty easy to use


- doesn't seem actively maintained but I don't know of an alternative


- dropped support for scala 2.12

---

```
 _____                      _                      _       
| ____|_  ___ __   ___ _ __(_)_ __ ___   ___ _ __ | |_ ___ 
|  _| \ \/ / '_ \ / _ \ '__| | '_ ` _ \ / _ \ '_ \| __/ __|
| |___ >  <| |_) |  __/ |  | | | | | | |  __/ | | | |_\__ \
|_____/_/\_\ .__/ \___|_|  |_|_| |_| |_|\___|_| |_|\__|___/
           |_|                                             
```

13 in total

Just look at the more interesting ones

---

# What we're measuring

> The approx time in ms doing something like:
>
> array.map(...).filter(...)  (some optimized version)
>
> for arrays of various sizes
>
> with values uniformly distributed in 0-99

ie.

```scala
val array = Array(56, 12, 0, 89, ....)

// Do this in different ways
array.map(_ * 2).filter(_ % 6 != 0)
```

---

# Machines

Already ran the experiments on 3 machines:

- doorstop - Rohan's old macbook pro (4 cores)


- robox - Rohan's linux desktop


- beast - the ec2 machine the dataiq team uses

---

# Doorstop

2015 Macbook pro

Quad-core i7, 2.2GHz 

16GB ram, 1600 MHz DDR3

---

# Beast 

ec2 instance

8 cores (hyper threaded)

3.098GHz

64GB ram

---

# Robox

Linux desktop machine 2015

Quad core i7 (hyper threaded), 3.6GHz

32GB ram, 1333 MHz DDR3

---

# Array sizes

- 250K


- 500K


- 750K


- 1M

---

# Sample output

```
[info] ::Benchmark Array.map.filter::
[info] cores: 8
[info] hostname: doorstop
[info] name: OpenJDK 64-Bit Server VM
[info] osArch: x86_64
[info] osName: Mac OS X
[info] vendor: AdoptOpenJDK
[info] version: 11.0.10+9
[info] Parameters(size -> 250000): 22.383412 ms
[info] Parameters(size -> 500000): 45.207693 ms
[info] Parameters(size -> 750000): 67.886285 ms
[info] Parameters(size -> 1000000): 91.005086 ms
```

It's linear going through the origin

We can characterize it with a single value

---

# Many experiments

Will use the value for 1M for:

```scala
array.map(_ * 2).filter(_ % 6 != 0)
```

---

# Time constraints

Not enough time to go through all experiments

Just hand picked the more interesting ones

---

# Brainstorming

Ideas for how you'd speed this up?

```scala
// 1M elements from 0-99 inclusive
val array: Array[Int] = ...

val processed = array.map(_ * 2).filter(_ % 6 != 0)
```

```
 ___ 
|__ \
  / /
 |_| 
 (_) 
     
```

---

# Quick summary of results

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
```

Lower = faster = gooderer

---

# Exp01

Standard library

```scala
array.map(_ * 2).filter(_ % 6 != 0)
```

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
```

Value for robox is _very_ variable: 36-82

---

# Exp02

"Predicate pushdown"

```scala
// Old
array.map(_ * 2).filter(_ % 6 != 0)

// New
array.filter(_ % 3 != 0).map(_ * 2)
array.collect {
  case i if i % 3 != 0 => i * 2
}
```

---

# Exp02

Run it!

---

# Results

```scala
// Old
array.map(_ * 2).filter(_ % 6 != 0)

// New
array.filter(_ % 3 != 0).map(_ * 2)
array.collect {
  case i if i % 3 != 0 => i * 2
}
```

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
| 2   | pred pushdown     | 70       | 26    | 22    |
|     | collect           | 41       | 19    | 16    |
------------------------------------------------------
```

---

# Exp03

`map` and `filter` use iterator based approaches

Let's try a more direct for-loop style approach

---

# Loop based

```scala
import spire.implicits.cfor

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
```

---

# Results

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
| 3   | cfor loop         | 61       | 53    | 43    |
------------------------------------------------------
```

---

# Exp04

Same `cfor` based approach but in parallel

---

# Parallel map

Allocate empty array of the same size

```
[ 32, 20, 67, 28, 90, 99, 11, 34, 66, 73, 44, 11, 33 ]


[                                                    ]
```

---

# Divide into slices and fill in parallel

```
  |-----------------| |-------------| |------------|
[ 32, 20, 67, 28, 90, 99, 11, 34, 66, 73, 44, 11, 33 ]

        _ * 2             _ * 2            _ * 2

  |-----------------| |-------------| |------------|
[ 64, 40, ...         198, 22, ...    146, 88, ...   ]
```

(Make sure to partition evenly)

---

# Code

```scala
    def mapC4Par[B: ClassTag](f: A => B)(implicit ec: C4ExecutionContext): Array[B] = {
      val slices = partitions(array.length, ec.parallelism)

      val mapped = Array.ofDim[B](array.length)

      val parallelized: Future[List[Unit]] = Future.traverse(slices) { case (start, stop) =>
        Future {
          cfor(start)(_ < stop, _ + 1) { i =>
            mapped(i) = f(array(i))
          }
        }(ec.threadPool)
      }

      Await.result(parallelized, Duration.Inf)

      mapped
    }

```

---

# Filtering

Trickier - you don't know how many results you'll get

Can't preallocate the final array and fill straight into it

---

# Buffers

```
  |-----------------| |-------------| |------------|
[ 32, 20, 67, 28, 90, 99, 11, 34, 66, 73, 48, 11, 30 ]
                  ^^              ^^      ^^      ^^

      _ * 6 != 0        _ % 6 != 0      _ % 6 != 0


       buffer              buffer          buffer    
 [ 32, 20, 67, 28 ]   [ 99, 11, 34 ]     [ 73, 11 ]


                       append             


        [ 32, 20, 67, 28, 99, 11, 34, 73, 11 ]
    
```

---

# Results

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
| 4   | cfor par (32)     |          |  7    | 14    |
|     | cfor par (16)     |          |  7    | 14    |
|     | cfor par (8)      | 16       |  8    | 16    |
|     | cfor par (6)      |          | 10    | 16    |
|     | cfor par (4)      | 22       | 14    | 19    |
|     | cfor par (2)      |          | 27    | 25    |
------------------------------------------------------
```

On beast, the parallelism doesn't help after 8

---

# Exp05

Avoid building intermediate collections

```scala
array.map(_ * 2).filter(_ % 6 != 0)

// is really

val intermediate = array.map(_ * 2)
val second = intermediate.filter(_ % 6 != 0)
```

---

# Fuse them together

A builder pattern

```scala
array.fuse.map(_ * 2).filter(_ % 6 != 0).boom
//    ^^^^                               ^^^^
```

---

# Horizontal instead of vertical

```
        fuse         map(_ * 2)    filter(_ % 6 != 0)      buffer     array
40 --> Some(40)  -->  Some(80)  -->     Some(80)            80         80
15 --> Some(15)  -->  Some(30)  -->     None                40         40
20 --> Some(20)  -->  Some(40)  -->     Some(40)
```

---

# Builder pattern

```scala
case class Fuse[A, B: ClassTag](array: Array[A], transform: A => Option[B]) {

  def map[C: ClassTag](f: B => C): Fuse[A, C] = Fuse(array, (a: A) => transform(a).map(f))
  def filter(pred: B => Boolean): Fuse[A, B] = Fuse(array, (a: A) => transform(a).filter(pred))

  def boom: Array[B] = {
    val buffer = ArrayBuffer.empty[B]

    val length = array.length
    cfor(0)(_ < length, _ + 1) { i =>
      transform(array(i)).foreach { survivor =>
        buffer.append(survivor)
      }
    }

    buffer.toArray
  }

}
```

---

# Results

Run it for fun!

---

# Results

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
| 2   | collect           | 41       | 19    | 16    |
------------------------------------------------------
| 5   | fuse              | 66       | 55    | 48    |
------------------------------------------------------
```

---

# Exp07

`ShortFuse`

Variant on `Fuse` intended to short circuit out

---

# Longer pipelines

We don't "bail" out when the pipeline generates a `None`

```scala
array.fuse.filter(_ > 50).map(_ * 2).filter(_ % 6 != 0).map(_ * 3).filter(_ > 300).boom
```

```
        fuse         filter(_ > 50)    map(_ * 2)   filter(_ % 6 != 0)  map(_ * 3)  filter(_ > 300)
40 --> Some(40)  -->     None     -->    None    -->      None    -->     None   -->    None

                       bail out?
```

Each extra step is a `map` or `filter` on the `None`

---

# `Fuse`

Can't bail out because the steps are glued together with composition:

```scala
case class Fuse[A, B: ClassTag](array: Array[A], transform: A => Option[B]) {

  def map[C: ClassTag](f: B => C): Fuse[A, C] = Fuse(array, (a: A) => transform(a).map(f))

  def filter(pred: B => Boolean): Fuse[A, B] = Fuse(array, (a: A) => transform(a).filter(pred))
  ...
}
```

The function is a black box

---

# Idea

Separate the individual steps into a more explicit collection of functions

Iterate through them with a `cfor` loop

Bail out when our data becomes `None`

---

# Problem

Have to throw away type information internally

```scala
case class ShortFuse[A, B](array: Array[A], transforms: Vector[Any => Option[Any]])
```

Internally we throw away the types, but our builder methods make sure everything is typesafe

---

# Results

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
| 5   | fuse              | 66       | 55    | 48    |
------------------------------------------------------
| 7   | shortfuse         | 64       | 56    | 51    |
------------------------------------------------------
```

No real speed improvement for our case

---

# Summary of Exp08

- less type safe


- more confusing and dirty


- no significant speedup for our map.filter case

---

# Exp09

Caching within a stage

---

# Caching

```
          _ * 2        _ % 6 != 0
13         26             true
62        124             true
13         26 (hit)       true
53        106             true
...       ...             ...
13         26 (hit)       true


caches   Map(             Map(
           13 -> 26,        26 -> true,
           62 -> 124,       124 -> true,
           53 -> 106,       106 -> true
           ...              ...
         )                )
```

---

# Value?

Makes sense if both are true:

- there's repetition in the values (yes!)


- the map/filter work is more than the time for a lookup (no!)

---

# Filtering on inner data

```scala
case class Person(name: String, age: Int)

people.filter(_.age > 18)
```

Cache would double up on people with the same age:

```
                        _.age > 18
Person("Boban", 26)          true
Person("Zij", 26)            true

                        Map(
                          Person("Boban", 26) -> true,
                          Person("Zij", 26) -> true
                        )
```

---

# Alternative?

```scala
case class Person(name: String, age: Int)

people.map(_.age).filter(_ > 18)
```

But now we've thrown away our name

---

# Problem: Transparency

The framework can't see into our predicate to see what field we're using:

```scala
people.filter(_.age > 18)
```

(Spark solves this problem with "structured data")

---

# Solution

Tell the framework which data matters

```scala
case class Person(name: String, age: Int)

people.filterByKey(_.age)(_ > 18)
```

Caches by age and doesn't throw away the name

---

# Results

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
| 9   | ByKey             | 100      | 83    | 73    |
------------------------------------------------------
```

Made it slower

```scala
array.mapByKey(i => i)(_ * 2).filterByKey(i => i)(_ % 6 != 0)
```

Not surprizing as `_ * 2` and `_ % 6 != 0` are faster than cache lookups

---

# Exp10

Parallelized version of Exp09

Each slice has its own cache

```
          _ * 2        _ % 6 != 0
------------------------------------
13         26             true
62        124             true
...
------------------------------------
13         26 (miss)      true
53        106             true
...       ...             ...
------------------------------------
13         26 (miss)      true
...
------------------------------------
```

---

# Simplicity

> Each slice has its own cache

Why?

Keeps things simple - no shared state or lock contention

---

# Implications

> Each slice has its own cache

- each key has a transform applied to it up to p times


- approximately p times more memory

---

# Results

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
| 4   | cfor par (4)      | 22       | 14    | 19    |
------------------------------------------------------
| 9   | ByKey             | 100      | 83    | 73    |
------------------------------------------------------
| 10  | ByKey par (4)     | 39       | 21    | 26    |
------------------------------------------------------
```

Bigger speed up on the beast

---

# Alternatives I tried

## Lock-striping

Exp11

One cache with fine grained locking

## Two phase approach

Exp14

Build a cache across first 500 elements

Then switch to parallel processing with read-only cache

---

# Exp12

`FragArray`

No time to talk about it though :sad-parrot:

---

# Exp13

The built in parallel collections library

(Note using the 2.12 version)

```scala
array.par.map(_ * 2).filter(_ % 6 != 0)
//    ^^^
```

---

# Results

Run it for fun!

---

# Results

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    |       |
------------------------------------------------------
| 4   | cfor par (32)     |          |  7    | 14    |
|     | cfor par (16)     |          |  7    | 14    |
|     | cfor par (8)      | 16       |  8    | 16    |
|     | cfor par (6)      |          | 10    | 16    |
|     | cfor par (4)      | 22       | 14    | 19    |
|     | cfor par (2)      |          | 27    | 25    |
------------------------------------------------------
| 13  | stdlib .par       | 22       | 9     | 10    |
------------------------------------------------------
```

Easy to use and strong improvement

My `cfor` based parallel implementation just squeezes under on the beast

---

# Factorial

Also had a test case like:

```scala
array.map(fac)
```

ie. calculate 1M factorials of numbers from 0-99

---

# Results

```
---------------------------------
| Exp | Description     | Beast |
---------------------------------
| 1   | stdlib          | 2579  |
---------------------------------
| 3   | cfor            | 2554  |
---------------------------------
| 9   | ByKey           |   34  |
---------------------------------
| 10  | ByKeyPar (4)    |    9  |
---------------------------------
| 14  | ByKeyWarmup (4) | TODO  |
---------------------------------
```

The cache really helps!

---

```
  ____ _           _             
 / ___| | ___  ___(_)_ __   __ _ 
| |   | |/ _ \/ __| | '_ \ / _` |
| |___| | (_) \__ \ | | | | (_| |
 \____|_|\___/|___/_|_| |_|\__, |
                           |___/ 
 _____ _                       _     _       
|_   _| |__   ___  _   _  __ _| |__ | |_ ___ 
  | | | '_ \ / _ \| | | |/ _` | '_ \| __/ __|
  | | | | | | (_) | |_| | (_| | | | | |_\__ \
  |_| |_| |_|\___/ \__,_|\__, |_| |_|\__|___/
                         |___/               
```

---

# Beast

Not such a beast,

slower than robox on non-parallel CPU intensive workloads

---

# Hyperthreading

Doesn't really do much for cpu intensive workloads:

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
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
```

---

# Parallel collections

The built in parallel collections did really well

```
------------------------------------------------------
| Exp | Description       | doorstop | beast | robox |
------------------------------------------------------
| 1   | stdlib            | 91       | 60    | 42    |
------------------------------------------------------
| 4   | cfor par (32)     |          |  7    | 14    |
|     | cfor par (16)     |          |  7    | 14    |
|     | cfor par (8)      | 16       |  8    | 16    |
|     | cfor par (6)      |          | 10    | 16    |
|     | cfor par (4)      | 22       | 14    | 19    |
|     | cfor par (2)      |          | 27    | 25    |
------------------------------------------------------
| 13  | stdlib .par       | 22       | 9     | 10    |
------------------------------------------------------
```

Would be cool to combine that with fusing steps

But it's not open-closed

---

# Absolute vs Relative

Be careful how you describe a speed up:

```
--------------------------------------
| Exp | Description       | doorstop |
--------------------------------------
| 1   | stdlib            | 91       |
--------------------------------------
| 3   | cfor loop         | 61       |
--------------------------------------
```

Absolute terms: 30ms per 1M

Relative terms: 91/61 ~ 3/2, ie. 1.5x speedup

---

# Remember factorial

```
------------------------------
| Exp | Description  | Beast |
------------------------------
| 1   | stdlib       | 2579  |
------------------------------
| 3   | cfor         | 2554  |
------------------------------
| 9   | ByKey        |   34  |
------------------------------
| 10  | ByKeyPar (4) |    9  |
------------------------------
```

Absolute terms: 25ms per 1M

Relative terms: 2579/2554 ~ 1

---

# My fear

> Dev1: Using cfor gives a 1.5x speedup for array iteration
>
> Dev2: Ooooooh!

---

# How I'd express it

> Using a cfor loop reduces time by Xms per 1M elements in the array (for some X)

---

# Parallelism

```
-----------------------------------
| Exp | Description       | beast |
-----------------------------------
| 4   | cfor par (8)      |  8    |
|     | cfor par (4)      | 14    |
|     | cfor par (2)      | 27    |
-----------------------------------
```

Here it makes sense to talk about in relative terms:

> Parallelizing with p workers increases performance p times (maxing out at some value)

---

# Mixing optimizations

Often when you're trying to speed things, you're trying different tricks

---

# Order

Suppose something takes 100ms

You find two optimizations

A - speeds it up by 4x (parallelism)

B - speeds it up by 10ms (e.g. cfor)

---

# Order of application

> Takes 100ms
>
> A - speeds it up by 4x (parallelism)
>
> B - speeds it up by 10ms

_Perceived_ improvement:

```
     A x4        B -10
100  --->  25    --->   90/4 = 22.5   (B reduced it by 2.5)

     B -10       A x4
100  --->  90    --->   90/4 = 22.5   (B reduced it by 10)
```

---

# First looks best

Usually the optimization you apply first looks like it has the most effect

---

# Bang for buck

We like simple optimizations that give big speedups

The order you mix them will skew your perspective

---

# Test individually

Look at each in isolation

Understand the nature of each (absolute vs relative)

Understand how they would interract

---

# Careful of metrics

Take the metrics with a grain of salt

---

# Examples of this

I noticed:

- using the same sbt session for repeated runs led to slower results (memory pressure?)


- the order experiments are run in a suite can affect metrics (possible JIT optimizations)


- machines behaved quite differently on some optimizations


- on robox, for Exp01, got 84 then 44 for subsequent runs...

---

# Metrics focus on time

They don't focus on memory

A very memory intensive optimization might give a big speedup but might not be practical

---

# Further Reading

- good article about [how scala handles arrays](https://docs.scala-lang.org/overviews/collections-2.13/arrays.html)


- [scalameter docs](https://scalameter.github.io/home/gettingstarted/0.7/index.html)


- good article about [scala's parallel collections](https://docs.scala-lang.org/overviews/parallel-collections/overview.html)


- my [C4 repo](https://github.com/rminehan/c4) which has the code for this demo


- [spire docs](https://typelevel.org/spire/)

---

# QnA
