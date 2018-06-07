PROGRAM first_program
	!First comment
	INTEGER::i, a = 2, b = 4, c

	!Second comment
	!$claw loop-fusion
	DO i = 1, 10
		!Body 1 comment
		WRITE(*,*) "first body, i = ", i
	END DO

	!$claw loop-fusion
	DO i = 1, 10
		!Body 2 comment
		WRITE(*,*) "second body, i = ", i
	END DO

	!$claw first-directive
	c = a * b

	!End comment
END PROGRAM first_program
