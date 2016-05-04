test:
	lein test
	lein with-profile +client-test doo node test once
