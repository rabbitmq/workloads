require "bunny"
require "json"

STDOUT.sync = true

DEBUG = ENV.fetch("DEBUG", "false") == "true"

class Client
  def initialize(name:, connection:, queueName:, message:)
    @connection = connection
    @queueName = queueName
    @message = message

    puts "Create #{name} for #{queue.name} "
  end

  def publish
    exchange.publish(message, routing_key: queueName)
    if DEBUG
        puts "Sending message ..."
    end
  end

  def get
    delivery_info, properties, payload = channel.basic_get(queueName, :manual_ack => false)
    if DEBUG
      if payload.nil?
        puts "No messages!"
      else
        puts "Received message !"
      end
    end
  end

  private

  attr_reader :connection, :queue, :queueName, :message

  def queue
    @queue = channel.queue(queueName, :exclusive => false, :auto_delete => true)
  end
  def channel
    @channel ||= connection.create_channel
  end

  def exchange
    @exchange ||= channel.default_exchange
  end
end


class Controller

  def initialize(publishersPerQueue:, consumersPerQueue:)

    number_of_queues = toQueueIndex - fromQueueIndex + 1
    number_of_publishers = publishersPerQueue * number_of_queues
    number_of_consumers = consumersPerQueue * number_of_queues
    # Each instance creates more queues but it continues with the sequence
    startingQueueIndex = instance_index * number_of_queues + fromQueueIndex

    puts "Setting up #{number_of_queues} queues with #{number_of_publishers} publishers and #{number_of_consumers} consumers "
    puts "Running with : "
    puts "       consumerThreads: #{consumerThreads}"
    puts "       publisherThreads: #{publisherThreads}"
    puts "       amqp_urls: #{amqp_urls}"
    puts "       basic_get_rate: #{basic_get_rate}/sec"
    puts "       from queue #{startingQueueIndex} to  #{startingQueueIndex+number_of_queues} "

    @publishers = []

    number_of_queues.times do |i|
      publishersPerQueue.times do |j|
        queueIndex = startingQueueIndex + i
        queueName = "#{queuePrefixName}-#{queueIndex}"
        amqp_url = amqp_urls[rand(amqp_urls.length)]
        puts "Connecting publisher #{i}-#{j} to #{amqp_url} ..."
        name = "Publisher- #{i}-#{j}"
        publishers <<
            Client.new(
                name: name,
                connection: Bunny.new(amqp_url).start,
                queueName: queueName,
                message: message)
      end
    end

    @consumers = []

    number_of_queues.times do |i|
      consumersPerQueue.times do |j|
        queueIndex = startingQueueIndex + i
        queueName = "#{queuePrefixName}-#{queueIndex}"
        amqp_url = amqp_urls[rand(amqp_urls.length)]
        puts "Connecting consumer #{i}-#{j} to #{amqp_url} ..."
        name = "Consumer- #{i}-#{j}"
        consumers <<
            Client.new(
                name: name,
                connection: Bunny.new(amqp_url).start,
                queueName: queueName,
                message: message)
      end
    end

  end

  def run
    runMultiThreaded
  end

  def runSingleThreaded
    loop do
      $publishCounter = 0
      $getCounter = 0

      t0 = Time.new.to_i
      if shallPublish
        publish_rate.times { publishers.map(&:publish) }
      end
      if shallConsume
        basic_get_rate.times { consumers.map(&:get) }
      end
      t1 = Time.new.to_i
      elapsed = t1-t0
      puts "Elapsed time #{elapsed} <= #{t0} vs #{t1} "

      if elapsed < 1
        sleep 1
      end
    end

  end

  def runMultiThreaded

    @workerThreads = []

    if shallConsume
      run_consumers
    end
    if shallPublish
      run_publishers
    end

    puts "Scheduled #{workerThreads.length} threads"
    workerThreads.each(&:join)

  end

  def run_publishers

    publishersPerThread = publishers.length / publisherThreads

    publisherThreads.times do |i|

      workerThreads << Thread.new {
        from = i*publishersPerThread
        to = [from+publishersPerThread-1, publishers.length-1].min

        puts "Launching thread #{i} with publishers [ #{from}..#{to} ]"
        loop do
          t0 = Time.new.to_i
          publish_rate.times { publishers[from..to].map(&:publish) }
          t1 = Time.new.to_i
          elapsed = t1-t0
          puts "P[#{i}] Finished @ #{t1} in #{elapsed} sec #{publish_rate} publish(s) using #{to-from+1} publishers"
          if (elapsed < 1)
            sleep 1
          end
        end
      }
    end

  end

  def run_consumers

    consumersPerThread = consumers.length / consumerThreads

    consumerThreads.times do |i|

      workerThreads << Thread.new {
        from = i*consumersPerThread
        to = [from+consumersPerThread-1, consumers.length-1].min

        puts "Launching thread #{i} with consumers [ #{from}..#{to} ]"
        loop do
          t0 = Time.new.to_i
          basic_get_rate.times { consumers[from..to].map(&:get) }
          t1 = Time.new.to_i
          elapsed = t1-t0
          puts "C[#{i}] Finished @ #{t1} in #{elapsed} sec #{basic_get_rate} basic.get(s) using #{to-from+1} consumers"
          if (elapsed < 1)
            sleep 1
          end
        end
      }
    end

  end

  attr_reader :publishers, :consumers

  private
  attr_reader :amqp_urls, :queuePrefixName, :instance_index, :defaultQueueName, :message, :min_msg_size, :max_msg_size,
            :vcap_services, :vcap_application, :publisherThreads, :consumerThreads, :workerThreads, :fromQueueIndex, :toQueueIndex

  def amqp_urls
    @amqp_urls = vcap_services.fetch("user-provided").
                  find { |s| s.fetch("name") == "rmq" }.
                  fetch("credentials").
                  fetch("urls")
  end

  def fromQueueIndex
    @fromQueueIndex = ENV.fetch("FROM_QUEUE_INDEX", 0).to_i
  end

  def toQueueIndex
    @toQueueIndex = ENV.fetch("TO_QUEUE_INDEX", 1).to_i
  end

  def publisherThreads
    @publisherThreads= ENV.fetch("PUBLISHER_THREADS", 1).to_i
  end

  def consumerThreads
    @consumerThreads= ENV.fetch("CONSUMER_THREADS", 1).to_i
  end

  def queuePrefixName
    @queuePrefixName= ENV.fetch("QUEUE_NAME", defaultQueueName)
  end

  def instance_index
    @instance_index = ENV.fetch("CF_INSTANCE_INDEX", 0).to_i
  end

  def defaultQueueName
    @defaultQueueName = vcap_application.fetch("application_name")
  end

  def message
    @message = rand(65..91).chr * rand(min_msg_size..max_msg_size)
  end

  def min_msg_size
    @min_msg_size =  ENV.fetch("MIN_MSG_SIZE_IN_BYTES", 1024).to_i
  end

  def max_msg_size
    @max_msg_size = ENV.fetch("MAX_MSG_SIZE_IN_BYTES", 10240).to_i
  end

  def publish_rate
    @publish_rate = ENV.fetch("PUBLISH_RATE_PER_QUEUE_PER_SECOND", 1).to_i
  end

  def basic_get_rate
    @basic_get_rate = ENV.fetch("BASIC_GET_RATE_PER_QUEUE_PER_SECOND", 1).to_i
  end

  def shallPublish
    @shallPublish = ENV.fetch("PUBLISH", "true") == "true"
  end
  def shallConsume
    @shallConsume = ENV.fetch("CONSUME", "true") == "true"
  end

  def amqp_urls
    @amqp_urls = vcap_services.fetch("user-provided").
                find { |s| s.fetch("name") == "rmq" }.
                fetch("credentials").
                fetch("urls")
  end

  def vcap_application
    @vcap_application = JSON.parse(ENV.fetch("VCAP_APPLICATION", JSON.dump(
      {
        "application_name": "threaded-synchronous-consumer"
      }
    )))
  end

  def vcap_services
    @vcap_services = JSON.parse(ENV.fetch("VCAP_SERVICES", JSON.dump(
      {
       "user-provided": [
          {
            "credentials": {
              "urls": ["amqp://guest:guest@127.0.0.1:5672", "amqp://guest:guest@127.0.0.1:5672"]
            },
            "name": "rmq"
          }
        ]
      }
      )))
  end

end



publishers_per_queue = ENV.fetch("PUBLISHERS_PER_QUEUE", 1).to_i
consumers_per_queue = ENV.fetch("CONSUMERS_PER_QUEUE", 1).to_i


Controller.new(
  publishersPerQueue: publishers_per_queue,
  consumersPerQueue: consumers_per_queue
  ).run()
