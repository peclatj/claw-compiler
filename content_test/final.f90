PROGRAM first_program
 INTEGER :: i

 DO i = 1 , 10 , 1
  WRITE ( unit = * , fmt = * )"first body, i = " , i
  WRITE ( unit = * , fmt = * )"second body, i = " , i
 END DO
END PROGRAM first_program

