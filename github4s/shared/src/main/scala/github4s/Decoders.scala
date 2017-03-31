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

package github4s

import cats.data.NonEmptyList
import cats.syntax.either._
import github4s.free.domain._
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._

/** Implicit circe decoders of domains objects */
object Decoders {
  case class Author(login: Option[String], avatar_url: Option[String], html_url: Option[String])

  implicit val decodeCommit: Decoder[Commit] = Decoder.instance { c ⇒
    for {
      sha     ← c.downField("sha").as[String]
      message ← c.downField("commit").downField("message").as[String]
      date    ← c.downField("commit").downField("author").downField("date").as[String]
      url     ← c.downField("html_url").as[String]
      author  ← c.downField("author").as[Option[Author]]
    } yield
      Commit(
        sha = sha,
        message = message,
        date = date,
        url = url,
        login = author.flatMap(_.login),
        avatar_url = author.flatMap(_.avatar_url),
        author_url = author.flatMap(_.html_url)
      )
  }

  implicit val decodeRepository: Decoder[Repository] = {

    def readRepoUrls(c: HCursor): Either[DecodingFailure, List[Option[String]]] = {
      RepoUrlKeys.allFields.foldLeft(
        Either.right[DecodingFailure, List[Option[String]]](List.empty)) {
        case (Left(e), name) => Left(e)
        case (Right(list), name) =>
          c.downField(name).as[Option[String]] match {
            case Left(e)         => Left(e)
            case Right(maybeUrl) => Right(list :+ maybeUrl)
          }
      }
    }

    Decoder.instance { c ⇒
      for {
        id                ← c.downField("id").as[Int]
        name              ← c.downField("name").as[String]
        full_name         ← c.downField("full_name").as[String]
        owner             ← c.downField("owner").as[User]
        priv              ← c.downField("private").as[Boolean]
        description       ← c.downField("description").as[String]
        fork              ← c.downField("fork").as[Boolean]
        created_at        ← c.downField("created_at").as[String]
        updated_at        ← c.downField("updated_at").as[String]
        pushed_at         ← c.downField("pushed_at").as[String]
        homepage          ← c.downField("homepage").as[Option[String]]
        language          ← c.downField("language").as[Option[String]]
        organization      ← c.downField("organization").as[Option[User]]
        size              ← c.downField("size").as[Int]
        stargazers_count  ← c.downField("stargazers_count").as[Int]
        watchers_count    ← c.downField("watchers_count").as[Int]
        forks_count       ← c.downField("forks_count").as[Int]
        open_issues_count ← c.downField("open_issues_count").as[Int]
        open_issues       ← c.downField("open_issues").as[Option[Int]]
        watchers          ← c.downField("watchers").as[Option[Int]]
        network_count     ← c.downField("network_count").as[Option[Int]]
        subscribers_count ← c.downField("subscribers_count").as[Option[Int]]
        has_issues        ← c.downField("has_issues").as[Boolean]
        has_downloads     ← c.downField("has_downloads").as[Boolean]
        has_wiki          ← c.downField("has_wiki").as[Boolean]
        has_pages         ← c.downField("has_pages").as[Boolean]
        url               ← c.downField("url").as[String]
        html_url          ← c.downField("html_url").as[String]
        git_url           ← c.downField("git_url").as[String]
        ssh_url           ← c.downField("ssh_url").as[String]
        clone_url         ← c.downField("clone_url").as[String]
        svn_url           ← c.downField("svn_url").as[String]
        repoUrls          ← readRepoUrls(c)
      } yield
        Repository(
          id = id,
          name = name,
          full_name = full_name,
          owner = owner,
          `private` = priv,
          description = description,
          fork = fork,
          created_at = created_at,
          updated_at = updated_at,
          pushed_at = pushed_at,
          homepage = homepage,
          language = language,
          organization = organization,
          status = RepoStatus(
            size = size,
            stargazers_count = stargazers_count,
            watchers_count = watchers_count,
            forks_count = forks_count,
            open_issues_count = open_issues_count,
            open_issues = open_issues,
            watchers = watchers,
            network_count = network_count,
            subscribers_count = subscribers_count,
            has_issues = has_issues,
            has_downloads = has_downloads,
            has_wiki = has_wiki,
            has_pages = has_pages
          ),
          urls = RepoUrls(
            url = url,
            html_url = html_url,
            git_url = git_url,
            ssh_url = ssh_url,
            clone_url = clone_url,
            svn_url = svn_url,
            otherUrls = (RepoUrlKeys.allFields zip repoUrls.flatten map {
              case (urlName, value) => urlName -> value
            }).toMap
          )
        )
    }
  }

  implicit val decodeGist: Decoder[Gist] = Decoder.instance { c ⇒
    for {
      url         ← c.downField("url").as[String]
      id          ← c.downField("id").as[String]
      description ← c.downField("description").as[String]
      public      ← c.downField("public").as[Boolean]
    } yield
      Gist(
        url = url,
        id = id,
        description = description,
        public = public
      )
  }

  val emptyListDecodingFailure = DecodingFailure("Empty Response", Nil)

  implicit def decodeListRef(implicit D: Decoder[Ref]): Decoder[NonEmptyList[Ref]] = {

    def decodeCursor(
        partialList: Option[NonEmptyList[Ref]],
        cursor: HCursor): Decoder.Result[NonEmptyList[Ref]] =
      cursor.as[Ref] map { r ⇒
        partialList map (_.concat(NonEmptyList(r, Nil))) getOrElse NonEmptyList(r, Nil)
      }

    def decodeCursors(cursors: List[HCursor]): Result[NonEmptyList[Ref]] =
      cursors.foldLeft[Decoder.Result[NonEmptyList[Ref]]](Left(emptyListDecodingFailure)) {
        case (Right(list), cursor)                      => decodeCursor(Some(list), cursor)
        case (Left(`emptyListDecodingFailure`), cursor) => decodeCursor(None, cursor)
        case (Left(e), _)                               => Left(e)
      }

    Decoder.instance { c ⇒
      c.as[Ref] match {
        case Right(r) => Right(NonEmptyList(r, Nil))
        case Left(e) =>
          c.as[List[HCursor]] flatMap decodeCursors
      }
    }
  }

}