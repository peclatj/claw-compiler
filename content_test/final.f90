PROGRAM first_program
 INTEGER :: i
 INTEGER :: a = 2
 INTEGER :: b = 4
 INTEGER :: c

 DO i = 1 , 10 , 1
  WRITE ( unit = * , fmt = * )"first body, i = " , i
  WRITE ( unit = * , fmt = * )"second body, i = " , i
 END DO
 c = b * b
END PROGRAM first_program

