{
	if (FNR == 1) {
		print $0
	} else {
		w[$0] = 1
	}
} END { 
	for (k in w) print k
}