# Things to know

The places where a plugin or a manager surprises its author.

## Settings are not there in the constructor
The engine creates the class by reflection with its public constructor without arguments, and **then** sets `properties` and the name. Read the settings in `init()`, not in the constructor. A class without a public constructor without arguments cannot be created.

## What a plugin sends runs on the sender's threads
An asynchronous event is run on the **pool of the sender**, not of the receiver: the receiver's `accept()` is called on a thread of the sender. So the number of threads (`nThread`) and the queue size of a slow receiver do not limit what it receives; the sender's do. When the sender's queue is full, the thread that sends runs the receiver's `accept()` itself.

## All instances of one jar share its classes
Every instance made from one jar uses the same class loader, so **static fields are shared** between the instances. Keep state in fields of the instance, not in static ones.

## Threads started in `init()` or `run()` are daemon threads
`init()` and `run()` run on a daemon thread of the engine, and a thread that you start there inherits that. Such a thread does not keep the service alive. Stop your own threads in `stop()`.

## A wiring with a cycle is allowed
`alkaprocessor.map.a=b` and `alkaprocessor.map.b=a` is accepted, and the engine warns about it. A **synchronous** event along a cycle calls itself until the stack overflows; asynchronous events keep circulating while the plugins keep forwarding.

## `accept()` and `process()` are called by several threads at once
Make them thread-safe. A scheduled task that throws is cancelled for good by the JDK: catch everything inside it.

## The name of the jar is how a service refers to it
A service names the file (`alkaprocessor.plugin.hello = myalka-plugin.jar`). The parent pom therefore builds the jar **without a version** in its name. The same for the folder of its libraries: `myalka-plugin.jar` has `myalka-plugin.lib/`.

## A jar of another API version is refused
Rebuild it against the version of the engine. The Studio shows a sign after such a jar in the palette.

## Windows locks a jar that a service uses
`mvn alka:deploy` cannot replace the jar of a running service on Windows. Stop the service first.

## Return `false` from `init()` to say that you could not start
Do not hide a failure: an instance that returns `false` is marked failed and skipped, the others go on, and the Studio shows it. An exception does the same.
