package common

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.motorq.meetup.CustomError
import org.mockito.stubbing.OngoingStubbing

object MockitoExtension {
  fun <T> OngoingStubbing<Either<CustomError, T>>.thenReturnObject(obj: T) {
    thenReturn(obj.right())
  }

  fun <T> OngoingStubbing<Either<CustomError, T>>.thenReturnError(customError: CustomError) {
    thenReturn(customError.left())
  }
}