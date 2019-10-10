/*
 * SonarQube
 * Copyright (C) 2009-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.almsettings;

import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.alm.setting.AlmSettingDto;
import org.sonar.db.alm.setting.ProjectAlmSettingDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.user.UserSession;

import static java.lang.String.format;
import static org.sonar.api.web.UserRole.ADMIN;

public class SetGithubBindingAction implements AlmSettingsWsAction {

  private static final String PARAM_ALM_SETTING = "almSetting";
  private static final String PARAM_PROJECT = "project";
  private static final String PARAM_REPOSITORY = "repository";

  private final DbClient dbClient;
  private final UserSession userSession;
  private final ComponentFinder componentFinder;

  public SetGithubBindingAction(DbClient dbClient, UserSession userSession, ComponentFinder componentFinder) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.componentFinder = componentFinder;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction("set_github_binding")
      .setDescription("Bind a GitHub ALM instance to a project.<br/>" +
        "If the project was already bound to a previous GitHub ALM instance, the binding will be updated to the new one." +
        "Requires the 'Administer' permission on the project")
      .setPost(true)
      .setSince("8.1")
      .setHandler(this);

    action.createParam(PARAM_ALM_SETTING)
      .setRequired(true)
      .setDescription("GitHub ALM setting key");
    action.createParam(PARAM_PROJECT)
      .setRequired(true)
      .setDescription("Project key");
    action.createParam(PARAM_REPOSITORY)
      .setRequired(true)
      .setMaximumLength(256)
      .setDescription("GitHub Repository");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    doHandle(request);
    response.noContent();
  }

  private void doHandle(Request request) {
    String almSetting = request.mandatoryParam(PARAM_ALM_SETTING);
    String projectKey = request.mandatoryParam(PARAM_PROJECT);
    String repository = request.mandatoryParam(PARAM_REPOSITORY);
    try (DbSession dbSession = dbClient.openSession(false)) {
      ComponentDto project = componentFinder.getByKey(dbSession, projectKey);
      userSession.checkComponentPermission(ADMIN, project);
      AlmSettingDto almSettingDto = dbClient.almSettingDao().selectByKey(dbSession, almSetting)
        .orElseThrow(() -> new NotFoundException(format("No ALM setting with almSetting '%s' has been found", almSetting)));
      dbClient.projectAlmSettingDao().insertOrUpdate(dbSession, new ProjectAlmSettingDto()
        .setProjectUuid(project.uuid())
        .setAlmSettingUuid(almSettingDto.getUuid())
        .setAlmRepo(repository)
        .setAlmSlug(null));
      dbSession.commit();
    }
  }

}
