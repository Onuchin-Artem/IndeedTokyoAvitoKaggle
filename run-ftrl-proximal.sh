#!/bin/sh
set -e
time nice -18 java  -Xmx10G -Xms10G -XX:+UseG1GC \
		-classpath "avito-feature-engineering/target/avito-feature-engineering.jar" \
		io.scaledml.ftrl.Main \
		--csv_delimiter $'\t' --skip_first --custom-format-class io.scaledml.avito.AvitoInputFormat \
		--features_number 1050000000 --threads 3 \
		$@ 
