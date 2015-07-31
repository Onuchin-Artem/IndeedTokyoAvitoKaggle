#!/usr/bin/python

# example and test
#
from brick_wall_build import task, set_artifact_path, run

set_artifact_path("/usr/local/var/IndeedTokyoAvito")

@task
def install_avito_feature_engineering(input_artifacts, output_artifact):
	run("mvn -f avito-feature-engineering/pom.xml clean install")
	run("mv avito-feature-engineering/target/avito-feature-engineering.jar " + output_artifact + "/")


def clicksStream_tsv():
	awk -F'\t' -f get-click-stream.awk datasets/sandbox/SampleTrainJoinedSearch.tsv \
		> datasets/sandbox/clicksStream.tsv

datasets/sandbox/clicksUserStream.tsv: datasets/sandbox/clicksStream.tsv
	cut -f 2,3 datasets/sandbox/clicksStream.tsv \
		> datasets/sandbox/clicksUserStream.tsv

datasets/sandbox/clicksIpStream.tsv: datasets/sandbox/clicksStream.tsv
		cut -f 1,3 datasets/sandbox/clicksStream.tsv \
		> datasets/sandbox/clicksIpStream.tsv


datasets/sandbox/phoneUserStream.tsv:
	cut -f 1,3 datasets/raw/PhoneRequestsStream.tsv \
		> datasets/sandbox/phoneUserStream.tsv

datasets/sandbox/phoneIpStream.tsv:
	cut -f 2,3 datasets/raw/PhoneRequestsStream.tsv \
		> datasets/sandbox/phoneIpStream.tsv

datasets/sandbox/FilterTrainSearchStream.tsv:
	awk -F'\t' -f filter-object-type-3.awk datasets/raw/trainSearchStream.tsv \
		> datasets/sandbox/FilterTrainSearchStream.tsv

datasets/sandbox/validationSearchInfo.tsv:
	awk -F'\t' -f split-search-info.awk \
		datasets/raw/validationSearchIds.tsv datasets/raw/SearchInfo.tsv


datasets/sandbox/SampleTrainJoinedSearch.tsv: datasets/sandbox/FilterTrainSearchStream.tsv datasets/sandbox/validationSearchInfo.tsv
	rm -f datasets/sandbox/SampleTrainJoinedSearch.tsv
	awk -v'KEY_NAME=SearchID' -v'PARTITIONS_NUM=2' -v'PARTITION=1' -F'\t' -f join-partition.awk \
		 datasets/sandbox/trainSearchInfo.tsv datasets/sandbox/FilterTrainSearchStream.tsv \
		 >> datasets/sandbox/SampleTrainJoinedSearch.tsv
	awk -v'KEY_NAME=SearchID' -v'PARTITIONS_NUM=2' -v'PARTITION=0' -F'\t' -f join-partition.awk \
		 datasets/sandbox/trainSearchInfo.tsv datasets/sandbox/FilterTrainSearchStream.tsv \
		 >> datasets/sandbox/SampleTrainJoinedSearch.tsv

datasets/sandbox/SampleTrainJoinedSearchAds.tsv: datasets/sandbox/SampleTrainJoinedSearch.tsv
	awk -v'KEY_NAME=AdID' -F'\t' -f join.awk \
		datasets/raw/AdsInfo.tsv datasets/sandbox/SampleTrainJoinedSearch.tsv \
		> datasets/sandbox/SampleTrainJoinedSearchAds.tsv



datasets/sandbox/SampleValidationJoinedSearch.tsv: datasets/sandbox/FilterTrainSearchStream.tsv datasets/sandbox/validationSearchInfo.tsv
	awk -v'KEY_NAME=SearchID' -F'\t' -f join.awk \
		 datasets/sandbox/validationSearchInfo.tsv datasets/sandbox/FilterTrainSearchStream.tsv \
		 > datasets/sandbox/SampleValidationJoinedSearch.tsv

datasets/sandbox/SampleValidationJoinedSearchAds.tsv: datasets/sandbox/SampleValidationJoinedSearch.tsv
	awk -v'KEY_NAME=AdID' -F'\t' -f join.awk \
		datasets/raw/AdsInfo.tsv datasets/sandbox/SampleValidationJoinedSearch.tsv \
		> datasets/sandbox/SampleValidationJoinedSearchAds.tsv





datasets/sandbox/FilterTestSearchStream.tsv:
	awk -F'\t' -f filter-object-type-3.awk datasets/raw/testSearchStream.tsv | \
		awk -F'\t' -f rearrangeTrainStream.awk \
		> datasets/sandbox/FilterTestSearchStream.tsv

datasets/sandbox/FilterTestSearchIds.tsv: datasets/sandbox/FilterTestSearchStream.tsv
	cut -f 1 datasets/sandbox/FilterTestSearchStream.tsv | awk -f distinct.awk \
		> datasets/sandbox/FilterTestSearchIds.tsv

datasets/sandbox/TestSearchInfo.tsv: datasets/sandbox/FilterTestSearchIds.tsv
	awk -v'KEY_NAME=SearchID' -F'\t' -f join.awk \
		 datasets/sandbox/FilterTestSearchIds.tsv datasets/raw/SearchInfo.tsv | \
		 sed 's/\t"\([^\t"][^\t"]*\)\t\([^\t"][^\t"]*\)"\t/\t"\1 \2"\t/' \
		 > datasets/sandbox/TestSearchInfo.tsv

datasets/sandbox/SampleTestJoinedSearch.tsv: datasets/sandbox/TestSearchInfo.tsv
	awk -v'KEY_NAME=SearchID' -F'\t' -f join.awk \
		 datasets/sandbox/TestSearchInfo.tsv datasets/sandbox/FilterTestSearchStream.tsv \
		 > datasets/sandbox/SampleTestJoinedSearch.tsv

datasets/sandbox/SampleTestJoinedSearchAds.tsv: datasets/sandbox/SampleTestJoinedSearch.tsv
	awk -v'KEY_NAME=AdID' -F'\t' -f join.awk \
		datasets/raw/AdsInfo.tsv datasets/sandbox/SampleTestJoinedSearch.tsv \
		> datasets/sandbox/SampleTestJoinedSearchAds.tsv




datasets/submission/model2-validate.bin: datasets/sandbox/SampleTrainJoinedSearchAds.tsv
	./run-ftrl-proximal.sh \
		-d datasets/sandbox/SampleTrainJoinedSearchAds.tsv \
		--l1 10 --l2 10 \
		--final_regressor datasets/submission/model2-validate.bin

datasets/submission/model2.bin: datasets/submission/model2-validate.bin datasets/sandbox/SampleValidationJoinedSearchAds.tsv
	./run-ftrl-proximal.sh \
		-d datasets/sandbox/SampleValidationJoinedSearchAds.tsv \
		--l1 10 --l2 10 \
		--final_regressor datasets/submission/model2.bin \
		--initial_regressor datasets/submission/model2-validate.bin

datasets/submission/submission2.tsv: datasets/submission/model2.bin datasets/sandbox/SampleTestJoinedSearchAds.tsv
	./run-ftrl-proximal-with-ids.sh \
		-d datasets/sandbox/SampleTestJoinedSearchAds.tsv \
		--testonly \
		--l1 10 --l2 10 \
		--initial_regressor datasets/submission/model2.bin \
		--predictions datasets/submission/submission2.tsv

validate-model2: install-avito-feature-engineering datasets/submission/model2-validate.bin datasets/sandbox/SampleValidationJoinedSearchAds.tsv 
	./run-ftrl-proximal.sh \
		-d datasets/sandbox/SampleValidationJoinedSearchAds.tsv \
		--l1 10 --l2 10  \
		--testonly \
		--initial_regressor datasets/submission/model2-validate.bin

datasets/submission/submission2.csv.gz: datasets/submission/submission2.tsv
	echo "ID,IsClick" > datasets/submission/submission2.csv
	sed 's/\t/,/' datasets/submission/submission2.tsv >> datasets/submission/submission2.csv
	gzip datasets/submission/submission2.csv
