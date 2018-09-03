# User Goals

This script simulates one or many *synchronous consumers* (i.e. calling `basic.get` to consumer messages rather than `basic.consume` which does it in an asynchronous manner). Some scenarios require this type of consumers when the frequency at which we call `basic.get` is very low and the number of consumers is also very small.

## Script introduction

`synchronous_consumer` is a *Ruby* script that allows us to simulate publishers and synchronous consumers (`basic.get`) with various level of concurrency. Below is the table of settings it accepts and its default values.

> Default value is always 1 unless it is explicitly said otherwise

| PROPERTY                                | DESCRIPTION          |
| -                                       | -                    |
| QUEUE_NAME                              | The script creates as many queues as per the expression `TO_QUEUE_INDEX - FROM_QUEUE_INDEX + 1`. Queues are named as `QUEUE_NAME-<index>``. Default (`synchronous_consumer`) |
| FROM_QUEUE_INDEX                        | (default: `1`) Index of the first queue
| TO_QUEUE_INDEX                          | (default: `1`) Index of the last queue        
| BASIC_GET_RATE_PER_QUEUE_PER_SECOND     | How many `basic.get` calls per second and per consumer    |
| CONSUMERS_PER_QUEUE                     | How many concurrent consumer connections per queue should be doing calling `basic.get` at the specified rate. If we declare 10 queues and CONSUMERS_PER_QUEUE=2, we have 20 concurrent consumers, 2 per each queue.   |
| CONSUMER_THREADS                        | How many threads shall we use for consumers only. The script will evenly distribute the consumers among the threads. e.g. if we have 10 queues with 10 consumers, and 5 consumer threads, each thread will simulate 2 unique consumers each |
| CONSUME                                 | (default: `true`) Enable or disable consumers. Useful if we only want to publish, `CONSUME=false` |
| PUBLISH_RATE_PER_QUEUE_PER_SECOND       | How many messages to publish per second and per publisher    |
| PUBLISHERS_PER_QUEUE                    | How many concurrent publisher per queue connections should be publishing at the specified rate   |
| PUBLISHER_THREADS                       | How many threads shall we use for producers only. The script will evenly distribute the producers among the threads |
| PUBLISH                                 | (default: `true`) Enable or disable publishing. Useful if we only want to consume, `PUBLISH=false, |
| DEBUG                                   | (default: `false`) Enable or disable verbose logging which prints out every `basic.get` call and `publish` calls. |


We can run the script locally or in *Cloud Foundry*. See below for details.

## How to run the workload locally

To run 10 synchronous consumers against their corresponding queues (i.e. 10 queues in total) run ./`synchronous_consumer.sh`. It will attempt to connect to RabbitMq on `localhost:5672`.
This script will only simulate consumers no producers (see `PUBLISH=false` in the script).

The script prints out every second an statement like this one:
`2018-09-03T13:44:22.67+0200 [APP/PROC/WEB/0] OUT C[6] Finished @ 1535975062 in 0 sec 10 basic.get(s) using 10 consumers`  

`C[6]` indicates that this is the consumer thread 6
`Finished @ 1535975062 in 0 sec ` indicates when it finished and how long it took in seconds to do its job, in this case, it has done `10 x 1` `basic.get` calls (`BASIC_GET_RATE_PER_QUEUE_PER_SECOND=10` and `CONSUMERS_PER_QUEUE=1`).


## How to deploy the workload in Cloud Foundry

Before you push the application make sure you have a *service instance* with the name **rmq** in the space where we are going to push the application.

To help setting up the *service instance* run these commands:

1. Use your RMQ's cluster credentials in this file `ups-rmq.json`
2. Run `cf cups -p ups-rmq.json`

To push 2 applications (1 consumer and 1 publisher app) run `cf push`.  Or `cf push synchronous_consumer` if you only want to run the consumers.

The default workload configured in the `manifest.yml` used by `cf push` will do this:
```
Creates 100 queues named synchronous_consumer-<0 to 99> with 1 consumer connection and 1 producer connection per queue. The consumer connection calls basic.get 10 times per second and the publisher connection publishes 5 messages per second. We will use 10 threads to spread the 100 consumers and 1 thread to spread the 100 producers.
```

`cf logs producer` produces statements like this one:
```
2018-09-03T13:49:21.87+0200 [APP/PROC/WEB/0] OUT P[0] Finished @ 1535975361 in 0 sec 5 publish(s) using 100 publishers
```

And `cf logs synchronous_consumer` produces statements like this one:
```
2018-09-03T13:50:13.22+0200 [APP/PROC/WEB/0] OUT C[6] Finished @ 1535975413 in 0 sec 10 basic.get(s) using 10 consumers
2018-09-03T13:50:13.22+0200 [APP/PROC/WEB/0] OUT C[9] Finished @ 1535975413 in 0 sec 10 basic.get(s) using 10 consumers
2018-09-03T13:50:13.23+0200 [APP/PROC/WEB/0] OUT C[5] Finished @ 1535975413 in 0 sec 10 basic.get(s) using 10 consumers
```


To increase the number of queues we only need to scale the application(s) (`cf scale synchronous-_consumer -i 2`) and the script will continue with the next 100 queues, i.e. the queues would be named `synchronous_consumer-<100 to 199>`.  

To increase the load on the existing queues, i.e. on `synchronous_consumer-<0 to 99>`, edit the `manifest.yml` and add more applications as follows. The sample below will double the load on the existing 100 queues.
```
- name: threaded_synchronous_consumer-0
  env:
    QUEUE_NAME: threaded_synchronous_consumer
    FROM_QUEUE_INDEX: 1
    TO_QUEUE_INDEX: 100
  instances: 1
- name: threaded_synchronous_consumer-1
  env:
    QUEUE_NAME: threaded_synchronous_consumer
    FROM_QUEUE_INDEX: 1
    TO_QUEUE_INDEX: 100
  instances: 1

```

## Gather message statistics

To monitor message stats we use the Rabbitmq Management `/overview` Endpoint and we extract the json node `message_stats` and look for the following metrics. Below we can see that we are calling `basic.get` at least `2318.4` times per second:
```
"get_no_ack": 40708564,
  "get_no_ack_details": {
    "rate": 2318.4
  },
```

Our synchronous consumer is using `basic.get` with *no-ack* (automatic acknowledgements). When we call `get-ok`, we can expect 2 type of response messages:
- `get-ok` if we get back a message
- `get-empty` if the queue is empty and there are no messages

> **Observations**:
>
> We have observed that `get_no_ack` only reports `basic.get` calls that return `get-ok` messages. In other words, if the queue is empty, `get_no_ack` will not report any calls. This means that we cannot safely use this metric to measure the frequency at which clients are calling `basic.get`.
>
> **Suggestion**:
>
> If we want to keep the current semantics of `get_no_ack` which only reports `basic.get` that return a message, we could add another `message_stats` metric like `get_empty` with its corresponding `get_empty_details.rate` metric too that reports the number of `basic.get` requests that returned `get-empty` messages.

On the other hand, we can observe via `tcpdump` that the broker is receiving those `basic.get` calls. The command below will capture the incoming traffic for 1 second and a maximum of 100000 tcp packets on port 5672. It will only capture the request messages not the response (i.e. `get-ok` or `get-empty` messages):
`timeout 1 tcpdump -A -s9001 -v -c 100000 dst port 5672 -w /tmp/rmq.pcap`

If we want to capture incoming and outgoing traffic (i.e. the reply messages) use this other command:
`timeout 1 tcpdump -A -s9001 -v -c 100000 port 5672 -w /tmp/rmq.pcap`

You can use [Wireshark](https://www.rabbitmq.com/amqp-wireshark.html) to inspect the file `/tmp/rmq.pcap`.


## Clear up

To delete all `auto-delete` queues, run the following command replacing `HOST`, `PORT`, `USER` and `PWD` with the credentials of an `administrator` user.

```
for q in `/usr/local/sbin/rabbitmqadmin -H <HOST> -P <PORT> -u <USER> -p <PWD> -f tsv -q list queues name`; do /usr/local/sbin/rabbitmqadmin -H <HOST> -P <PORT> -u <USER> -p <PWD> -q delete queue name="$q"; done
```
