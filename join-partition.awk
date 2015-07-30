# left part must have unique keys
{ 
	if (FNR == 1) {
		key = -1
		for (i = 1; i <= NF; i++) {
			if ($i == KEY_NAME) {
				key = i
				print "key column = "key > "/dev/stderr"
			}
		}
	}
	if (FNR == NR) {
		if (NR % PARTITIONS_NUM == PARTITION) {
			line = "";
			for (i = 1; i <= NF; i++) {
				if (i != key) {
					line = line""$i"\t"
				}
			}
			left[$key] = line;
		}
	}
	if (FNR < NR) {
		if ($key in left) {
			print left[$key]""$0
		}
	}
}
