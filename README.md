Using zipkin/brave with Kafka streams
=

Note that this code was compiled against a WIP brave:5.4.4-SNAPSHOT branch.

Before trying to compile you should build https://github.com/openzipkin/brave locally with the following addition.

    git checkout -b jeqo-kafka-streams-processor master
    git pull https://github.com/jeqo/brave.git kafka-streams-processor

This however may stop working at anytime... you've been warned.

To try it out:

    mvn clean install
    docker-compose up -d
    
Then open http://localhost:9411/
