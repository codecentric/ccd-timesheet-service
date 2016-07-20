package de.codecentric.ccdashboard.service.timesheet.messages

import de.codecentric.ccdashboard.service.timesheet.data.model.Worklog

/**
  * Created by bjacobs on 20.07.16.
  */
case class WorklogQueryResult(w: Seq[Worklog])
