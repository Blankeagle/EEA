export MAVEN_OPTS="-Xmx18g" 
mvn exec:java -pl event-mention-detection -Dexec.mainClass="edu.cmu.cs.lti.emd.pipeline.CandidateEventMentionDetectorRunner" -Dexec.args=$1" "$2
