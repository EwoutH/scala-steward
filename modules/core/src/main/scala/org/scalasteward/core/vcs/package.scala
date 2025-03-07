/*
 * Copyright 2018-2022 Scala Steward contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalasteward.core

import cats.syntax.all._
import org.http4s.Uri
import org.scalasteward.core.data.ReleaseRelatedUrl.VersionDiff
import org.scalasteward.core.data.{ReleaseRelatedUrl, Version}
import org.scalasteward.core.git.Branch
import org.scalasteward.core.vcs.VCSType.{Bitbucket, BitbucketServer, GitHub, GitLab}
import org.scalasteward.core.vcs.data.Repo

package object vcs {

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for searching
    * for already existing pull requests.
    */
  def listingBranch(vcsType: VCSType, fork: Repo, updateBranch: Branch): String =
    vcsType match {
      case GitHub =>
        s"${fork.owner}/${fork.repo}:${updateBranch.name}"

      case GitLab | Bitbucket | BitbucketServer =>
        updateBranch.name
    }

  /** Determines the `head` (GitHub) / `source_branch` (GitLab, Bitbucket) parameter for creating
    * a new pull requests.
    */
  def createBranch(vcsType: VCSType, fork: Repo, updateBranch: Branch): String =
    vcsType match {
      case GitHub =>
        s"${fork.owner}:${updateBranch.name}"

      case GitLab | Bitbucket | BitbucketServer =>
        updateBranch.name
    }

  def possibleTags(version: Version): List[String] =
    List(s"v$version", version.value, s"release-$version")

  val possibleChangelogFilenames: List[String] = {
    val baseNames = List(
      "CHANGELOG",
      "Changelog",
      "changelog",
      "CHANGES"
    )
    possibleFilenames(baseNames)
  }

  val possibleReleaseNotesFilenames: List[String] = {
    val baseNames = List(
      "ReleaseNotes",
      "RELEASES",
      "Releases",
      "releases"
    )
    possibleFilenames(baseNames)
  }

  private[this] def extractRepoVCSType(
      vcsType: VCSType,
      vcsUri: Uri,
      repoUrl: Uri
  ): Option[VCSType] =
    repoUrl.host.flatMap { repoHost =>
      if (vcsUri.host.contains(repoHost)) Some(vcsType)
      else VCSType.fromPublicWebHost(repoHost.value)
    }

  def possibleCompareUrls(
      vcsType: VCSType,
      vcsUri: Uri,
      repoUrl: Uri,
      currentVersion: Version,
      nextVersion: Version
  ): List[VersionDiff] =
    extractRepoVCSType(vcsType, vcsUri, repoUrl).map {
      case GitHub | GitLab =>
        possibleTags(currentVersion).zip(possibleTags(nextVersion)).map { case (from1, to1) =>
          VersionDiff(repoUrl / "compare" / s"$from1...$to1")
        }
      case Bitbucket | BitbucketServer =>
        possibleTags(currentVersion).zip(possibleTags(nextVersion)).map { case (from1, to1) =>
          VersionDiff((repoUrl / "compare" / s"$to1..$from1").withFragment("diff"))
        }
    }.orEmpty

  def possibleReleaseRelatedUrls(
      vcsType: VCSType,
      vcsUri: Uri,
      repoUrl: Uri,
      currentVersion: Version,
      nextVersion: Version
  ): List[ReleaseRelatedUrl] = {
    val repoVCSType = extractRepoVCSType(vcsType, vcsUri, repoUrl)

    val github = repoVCSType
      .collect { case GitHub =>
        possibleTags(nextVersion).map(tag =>
          ReleaseRelatedUrl.GitHubReleaseNotes(repoUrl / "releases" / "tag" / tag)
        )
      }
      .getOrElse(List.empty)

    def files(fileNames: List[String]): List[Uri] = {
      val maybeSegments = repoVCSType.map {
        case GitHub | GitLab             => List("blob", "master")
        case Bitbucket | BitbucketServer => List("master")
      }

      maybeSegments.toList.flatMap { segments =>
        val base = segments.foldLeft(repoUrl)(_ / _)
        fileNames.map(name => base / name)
      }
    }

    val customChangelog = files(possibleChangelogFilenames).map(ReleaseRelatedUrl.CustomChangelog)
    val customReleaseNotes =
      files(possibleReleaseNotesFilenames).map(ReleaseRelatedUrl.CustomReleaseNotes)

    github ++ customReleaseNotes ++ customChangelog ++
      possibleCompareUrls(vcsType, vcsUri, repoUrl, currentVersion, nextVersion)
  }

  private def possibleFilenames(baseNames: List[String]): List[String] = {
    val extensions = List("md", "markdown", "rst")
    (baseNames, extensions).mapN { case (base, ext) => s"$base.$ext" }
  }
}
