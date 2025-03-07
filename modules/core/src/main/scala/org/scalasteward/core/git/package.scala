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

import org.scalasteward.core.data.{AnUpdate, GroupedUpdate, Update}
import org.scalasteward.core.repoconfig.CommitsConfig
import org.scalasteward.core.vcs.data.Repo

package object git {
  type GitAlg[F[_]] = GenGitAlg[F, Repo]

  val gitBlameIgnoreRevsName = ".git-blame-ignore-revs"

  val updateBranchPrefix = "update"

  def branchFor(update: AnUpdate, baseBranch: Option[Branch]): Branch = {
    val base = baseBranch.fold("")(branch => s"${branch.name}/")
    update match {
      case g: GroupedUpdate => Branch(s"$updateBranchPrefix/$base${g.name}")
      case u: Update        => Branch(s"$updateBranchPrefix/$base${u.name}-${u.nextVersion}")
    }
  }

  def commitMsgFor(
      update: Update,
      commitsConfig: CommitsConfig,
      branch: Option[Branch]
  ): CommitMsg =
    CommitMsg.replaceVariables(commitsConfig.messageOrDefault)(update, branch)
}
