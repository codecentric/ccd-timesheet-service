package de.codecentric.ccdashboard.service.timesheet.dataimport.schema

import slick.driver.H2Driver.api._

/**
  * Created by bjacobs on 13.07.16.
  */
class DummyData(tag: Tag) extends Table[(String, Int, Double)](tag, "Dummys") {
  def name = column[String]("DUMMY_NAME", O.PrimaryKey)

  def age = column[Int]("DUMMY_AGE")

  def height = column[Double]("DUMMY_HEIGHT")

  def * = (name, age, height)
}
