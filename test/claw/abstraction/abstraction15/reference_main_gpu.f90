PROGRAM test_abstraction15
 USE mo_column , ONLY: compute
 REAL :: t ( 1 : 60 , 1 : 20 )
 REAL :: z ( 1 : 60 )
 INTEGER :: nproma
 INTEGER :: nz
 INTEGER :: p
 INTEGER :: b
 REAL :: q ( 1 : 20 , 1 : 60 )

 nproma = 20
 nz = 60
 b = 20
 DO p = 1 , nproma , 1
  q ( p , 1 ) = 0.0
  z ( p ) = 0.0
  t ( 1 , p ) = 0.0
 END DO
!$ACC data copyin(q,t) copyout(q,t)
 CALL compute ( nz , b , q , t , z , nproma = nproma )
!$ACC end data
 PRINT * , sum ( q )
 PRINT * , sum ( t )
END PROGRAM test_abstraction15

