# job-hunt

This application is my solution to the coding exercise I worked on in March,
2015.

*-- Tyler Perkins, 2015-03-16*

## Problem Description

Create an HTTP service for tracking progress of background jobs. You can use whatever language or technology you prefer.

The job is represented by:

* `id` - unique job’s identifier
* `total` - a number representing total size of the job
* `progress` - a number representing current progress of the job (ie. job is considered completed when progress == total)

The service interface should support the following actions:

* Register a job - should accept `total` attribute, generate and return an ID for the job.
* Update a job based on ID - should allow to increment job's `progress` by some arbitrary number or set absolute `progress` - returns the new `progress`
* Show all jobs
* Show individual job by ID

Jobs should also expire after they have not been updated for 1 minute (ie. they would no longer appear in Show all jobs, and would not be returned when showing that job by ID).

The job state can be stored in memory (ie. doesn’t have to be persistent) or some external storage engine.

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## License

Copyright © 2015, Tyler Perkins
