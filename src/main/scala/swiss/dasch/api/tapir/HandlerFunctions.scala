package swiss.dasch.api.tapir

import swiss.dasch.api.ApiProblem
import swiss.dasch.api.ApiProblem.{ InternalServerError, NotFound }
import swiss.dasch.domain.ProjectShortcode

trait HandlerFunctions {

  def projectNotFoundOrServerError(mayBeError: Option[Throwable], shortcode: ProjectShortcode): ApiProblem =
    mayBeError.map(InternalServerError(_)).getOrElse(NotFound(shortcode))
}
