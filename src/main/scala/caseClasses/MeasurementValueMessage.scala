package caseClasses

import java.sql.Timestamp

case class MeasurementValueMessage(filename : String, timestamp: Timestamp, degC: Float)
