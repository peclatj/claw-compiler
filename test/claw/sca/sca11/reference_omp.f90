MODULE mo_column

CONTAINS
 FUNCTION compute_column ( nz , q , t , nproma ) RESULT(r)
  INTEGER , INTENT(IN) :: nproma
  INTEGER , INTENT(IN) :: nz
  REAL , INTENT(INOUT) :: t ( : , : )
  REAL , INTENT(INOUT) :: q ( : , : )
  INTEGER :: k
  REAL :: c
  INTEGER :: r
  INTEGER :: proma

!$omp target
!$omp teams thread_limit(256) num_teams(65536)
!$omp distribute dist_schedule(static, 256)
  DO proma = 1 , nproma , 1
   c = 5.345
   DO k = 2 , nz , 1
    t ( k , proma ) = c * k
    q ( k , proma ) = q ( k - 1 , proma ) + t ( k , proma ) * c
   END DO
   q ( nz , proma ) = q ( nz , proma ) * c
  END DO
!$omp end distribute
!$omp end teams
!$omp end target
 END FUNCTION compute_column

 SUBROUTINE compute ( nz , q , t , nproma )
  INTEGER , INTENT(IN) :: nproma

  INTEGER , INTENT(IN) :: nz
  REAL , INTENT(INOUT) :: t ( : , : )
  REAL , INTENT(INOUT) :: q ( : , : )
  INTEGER :: result

  result = compute_column ( nz , q , t , nproma = nproma )
 END SUBROUTINE compute

END MODULE mo_column

