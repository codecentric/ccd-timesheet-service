package de.codecentric.ccdashboard.service.timesheet.data.access

import slick.driver.JdbcDriver

/**
  * Created by bjacobs on 15.07.16.
  */
class DAO(val driver: JdbcDriver) {
  lazy val worklogDAO = new WorklogDAO(driver)
}
