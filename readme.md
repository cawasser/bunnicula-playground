# Bunnicula-Playground

#### a little project to play with [Bunnicula](https://github.com/nomnom-insights/nomnom.bunnicula) and [RabbitMQ](https://www.rabbitmq.com)

This project was designed for [REPL-Driven Development](https://clojure.org/guides/repl/introduction) so it is recommended you work in your editor-of-choice
and use the "evaluate in REPL" functionality of that tool

## Now with 100% More Protocol Buffers!

- [protobuf-queue](src/clj/protobuf_queue.clj)
- [Protocol Buffers](https://developers.google.com/protocol-buffers/)
- [clojusc/protobuf](https://github.com/clojusc/protobuf)


## Setup

1. install RabbitMQ on your development system
   - MacOS - suggest using ["How to install RabbitMQ on Mac using Homebrew"](https://www.dyclassroom.com/howto-mac/how-to-install-rabbitmq-on-mac-using-homebrew)
   - Windows - ["Installing RabbitMQ On Windows Using Scoop"](https://www.kongsli.net/2015/10/05/installing-rabbitmq-on-windows-using-scoop/)
2. Start up Editor/IDE
3. Start a REPL
4. Pick a clojure namespace (both do the same kind of things)
   1. [Plain clojure message content](src/clj/service.clj)
   2. [Protobuf over RabbitMQ](src/clj/protobuf_queue.clj)
5. Evaluate code


## Admin/Watch RabbitMQ

open your browser to:

    http://localhost:15672

Using the console, be sure to set the following:

tab      | values to create/set
---------|-----------------------
vHost    |  /main
exchange |  my-exchange
queues   |  some-queue, user-mgmt, calculate, calculate-result


## Bunnicula

see also [Bunnicula, asynchronous messaging with RabbitMQ for Clojure](https://blog.getenjoyhq.com/bunnicula-asynchronous-messaging-with-rabbitmq-for-clojure/)


## Protobuf

see also [protobuf tutorial](https://clojusc.github.io/protobuf/current/1050-tutorial.html)


