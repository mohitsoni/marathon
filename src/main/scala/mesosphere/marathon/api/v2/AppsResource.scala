package mesosphere.marathon.api.v2

import javax.ws.rs._
import scala.Array
import javax.ws.rs.core.{Response, Context, MediaType}
import javax.inject.{Named, Inject}
import mesosphere.marathon.event.EventModule
import com.google.common.eventbus.EventBus
import mesosphere.marathon.MarathonSchedulerService
import mesosphere.marathon.tasks.TaskTracker
import java.util.logging.Logger
import com.codahale.metrics.annotation.Timed
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid
import mesosphere.marathon.api.v1.{Implicits, AppDefinition}
import scala.concurrent.Await
import mesosphere.marathon.event.ApiPostEvent
import mesosphere.marathon.BadRequestException
import javax.ws.rs.core.Response.Status

/**
 * @author Tobi Knaup
 */

@Path("v2/apps")
@Produces(Array(MediaType.APPLICATION_JSON))
class AppsResource @Inject()(
                              @Named(EventModule.busName) eventBus: Option[EventBus],
                              service: MarathonSchedulerService,
                              taskTracker: TaskTracker) {

  val log = Logger.getLogger(getClass.getName)

  @GET
  @Timed
  def index(@QueryParam("cmd") cmd: String) = {
    val apps = if (cmd != null) {
      search(cmd)
    } else {
      service.listApps()
    }
    Map("apps" -> apps)
  }

  @POST
  @Timed
  def create(@Context req: HttpServletRequest, @Valid app: AppDefinition): Response = {

    // Return a 400: Bad Request if container options are supplied
    // with the default executor
    if (containerOptsAreInvalid(app)) throw new BadRequestException(
      "Container options are not supported with the default executor"
    )

    else {
      maybePostEvent(req, app)
      Await.result(service.startApp(app), service.defaultWait)
      Response.noContent.build
    }
  }

  @GET
  @Path("{id}")
  @Timed
  def show(@PathParam("id") id: String): Response = {
    service.getApp(id) match {
      case Some(app) => {
        app.tasks = taskTracker.get(app.id).toSeq
        Response.ok(Map("app" -> app)).build
      }
      case None => Response.status(Status.NOT_FOUND).build
    }
  }

  @PUT
  @Path("{id}")
  @Timed
  def update(
    @Context req: HttpServletRequest,
    @PathParam("id") id: String,
    @Valid appUpdate: AppUpdate
  ): Response = {
    service.getApp(id) match {
      case Some(app) => {
        val updatedApp = appUpdate.apply(app)

        // Return a 400: Bad Request if container options are supplied
        // with the default executor
        if (containerOptsAreInvalid(app)) throw new BadRequestException(
          "Container options are not supported with the default executor"
        )

        else {
          maybePostEvent(req, updatedApp)
          Await.result(service.updateApp(id, appUpdate), service.defaultWait)
          Response.noContent.build
        }
      }
      case None => Response.status(Status.NOT_FOUND).build
    }
  }

  @DELETE
  @Path("{id}")
  @Timed
  def delete(@Context req: HttpServletRequest, @PathParam("id") id: String): Response = {
    val app = new AppDefinition
    app.id = id
    maybePostEvent(req, app)
    Await.result(service.stopApp(app), service.defaultWait)
    Response.noContent.build
  }

  @Path("{appId}/tasks")
  def appTasksResource() = new AppTasksResource(service, taskTracker)

  private def containerOptsAreInvalid(app: AppDefinition): Boolean =
    (app.executor == "" || app.executor == "//cmd") && app.container.isDefined

  private def maybePostEvent(req: HttpServletRequest, app: AppDefinition) {
    if (eventBus.nonEmpty) {
      val ip = req.getRemoteAddr
      val path = req.getRequestURI
      eventBus.get.post(new ApiPostEvent(ip, path, app))
    }
  }

  private def search(cmd: String) = {
    service.listApps().filter {
      x =>
        var valid = true
        if (cmd != null && !cmd.isEmpty && !x.cmd.toLowerCase.contains(cmd.toLowerCase)) {
          valid = false
        }
        // Maybe add some other query parameters?
        valid
    }
  }
}
