package caseClasses

import java.sql.Timestamp

case class MeasurementValueMessage(timestamp: Timestamp, degC: Float)
