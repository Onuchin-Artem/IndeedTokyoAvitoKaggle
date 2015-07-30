{
	if (FNR == 1) {
		for (i = 1; i <= NF; i++) {
			if ($i == KEY_NAME) {
				key = i
				print "key column = "key > "/dev/stderr"

			}
		}
	} else {
		hash=($key * 877 + 863);
		if (hash % 101 < PERCENTS) print $0;
	}
}