package forex

package object programs {
  type RatesProgram[F[_]] = rates.Algebra[F]
  final val RatesProgram = rates.Program

  type AuthProgram[F[_]] = auth.Algebra[F]
  final val AuthProgram = auth.Program
}
