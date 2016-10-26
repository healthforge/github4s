/*
 * Copyright (c) 2016 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package github4s.free.interpreters

import cats.implicits._
import cats.{ApplicativeError, Eval, MonadError, ~>}
import github4s.GithubDefaultUrls._
import github4s.HttpClientExtension
import github4s.api.{Auth, Gists, Repos, Users}
import github4s.app.{COGH01, COGH02, GitHub4s}
import github4s.free.algebra._
import io.circe.Decoder
import simulacrum.typeclass

@typeclass
trait Capture[M[_]] {
  def capture[A](a: ⇒ A): M[A]
}

class Interpreters[M[_], C](implicit A: ApplicativeError[M, Throwable],
                            C: Capture[M],
                            httpClientImpl: HttpClientExtension[C, M]) {

  implicit def interpreters(
      implicit A: MonadError[M, Throwable]
  ): GitHub4s ~> M = {
    val c01interpreter: COGH01 ~> M = repositoryOpsInterpreter or userOpsInterpreter
    val c02interpreter: COGH02 ~> M = gistOpsInterpreter or c01interpreter
    val all: GitHub4s ~> M          = authOpsInterpreter or c02interpreter
    all
  }

  /**
    * Lifts Repository Ops to an effect capturing Monad such as Task via natural transformations
    */
  def repositoryOpsInterpreter: RepositoryOp ~> M = new (RepositoryOp ~> M) {

    val repos = new Repos()

    def apply[A](fa: RepositoryOp[A]): M[A] = fa match {
      case GetRepo(owner, repo, accessToken) ⇒ repos.get(accessToken, owner, repo)
      case ListCommits(owner, repo, sha, path, author, since, until, pagination, accessToken) ⇒
        repos.listCommits(accessToken, owner, repo, sha, path, author, since, until, pagination)
      case ListContributors(owner, repo, anon, accessToken) ⇒
        repos.listContributors(accessToken, owner, repo, anon)
    }
  }

  /**
    * Lifts User Ops to an effect capturing Monad such as Task via natural transformations
    */
  def userOpsInterpreter: UserOp ~> M =
    new (UserOp ~> M) {

      val users = new Users()

      def apply[A](fa: UserOp[A]): M[A] = fa match {
        case GetUser(username, accessToken) ⇒ users.get(accessToken, username)
        case GetAuthUser(accessToken)       ⇒ users.getAuth(accessToken)
        case GetUsers(since, pagination, accessToken) ⇒
          users.getUsers(accessToken, since, pagination)
      }
    }

  /**
    * Lifts Auth Ops to an effect capturing Monad such as Task via natural transformations
    */
  def authOpsInterpreter: AuthOp ~> M =
    new (AuthOp ~> M) {

      val auth = new Auth()

      def apply[A](fa: AuthOp[A]): M[A] = fa match {
        case NewAuth(username, password, scopes, note, client_id, client_secret) ⇒
          auth.newAuth(username, password, scopes, note, client_id, client_secret)
        case AuthorizeUrl(client_id, redirect_uri, scopes) ⇒
          auth.authorizeUrl(client_id, redirect_uri, scopes)
        case GetAccessToken(client_id, client_secret, code, redirect_uri, state) ⇒
          auth.getAccessToken(client_id, client_secret, code, redirect_uri, state)
      }
    }

  /**
    * Lifts Gist Ops to an effect capturing Monad such as Task via natural transformations
    */
  def gistOpsInterpreter: GistOp ~> M =
    new (GistOp ~> M) {

      val gists = new Gists()

      def apply[A](fa: GistOp[A]): M[A] = fa match {
        case NewGist(description, public, files, accessToken) ⇒
          gists.newGist(description, public, files, accessToken)
      }
    }

}
