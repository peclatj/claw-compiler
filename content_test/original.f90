PROGRAM first_program
	!First comment
	INTEGER::i

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
	!End comment
END PROGRAM first_program
