{
	if (FNR == 1) {
		key = -1
		for (i = 1; i <= NF; i++) {
			if ($i == "ObjectType") {
				key = i
				print "key column = "key > "/dev/stderr"
			}
		}
		print $0
	} else {
		if ($key=="3") {
			print $0
		}
	}
}