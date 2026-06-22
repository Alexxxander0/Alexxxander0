#!/bin/bash
sed -i 's/<maven.compiler.source>25<\/maven.compiler.source>/<maven.compiler.source>21<\/maven.compiler.source>/' DeltaEvents/pom.xml
sed -i 's/<maven.compiler.target>25<\/maven.compiler.target>/<maven.compiler.target>21<\/maven.compiler.target>/' DeltaEvents/pom.xml
cd DeltaEvents
mvn clean package
