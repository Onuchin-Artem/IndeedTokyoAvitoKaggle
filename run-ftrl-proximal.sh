#!/bin/sh
set -e
time nice -18 java  -Xmx15G -Xms15G -XX:+UseG1GC \
		-classpath "avito-feature-engineering/target/avito-feature-engineering-1.0-SNAPSHOT.jar:ftrl-proximal.jar" \
		io.scaledml.ftrl.Main \
		--csv_delimiter $'\t' --skip_first --custom-format-class io.scaledml.avito.AvitoInputFormat \
		--features_number 1850000000 --threads 3 \
		$@ 
