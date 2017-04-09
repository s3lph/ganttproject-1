/*
Copyright 2012 GanttProject Team

This file is part of GanttProject, an opensource project management tool.

GanttProject is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

GanttProject is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
*/
package biz.ganttproject.impex.csv;

import biz.ganttproject.core.model.task.TaskDefaultColumn;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import junit.framework.TestCase;
import net.sourceforge.ganttproject.CustomPropertyDefinition;
import net.sourceforge.ganttproject.TestSetupHelper;
import net.sourceforge.ganttproject.TestSetupHelper.TaskManagerBuilder;
import net.sourceforge.ganttproject.language.GanttLanguage;
import net.sourceforge.ganttproject.resource.HumanResource;
import net.sourceforge.ganttproject.resource.HumanResourceManager;
import net.sourceforge.ganttproject.roles.RoleManager;
import net.sourceforge.ganttproject.roles.RoleManagerImpl;
import net.sourceforge.ganttproject.task.Task;
import net.sourceforge.ganttproject.task.TaskContainmentHierarchyFacade;
import net.sourceforge.ganttproject.task.TaskManager;
import net.sourceforge.ganttproject.task.dependency.TaskDependency;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Map;

import static biz.ganttproject.impex.csv.SpreadsheetFormat.CSV;
import static biz.ganttproject.impex.csv.SpreadsheetFormat.XLS;
import static com.google.common.base.Charsets.UTF_8;

/**
 * Tests spreadsheet (CSV and XLS) import with GP semantics.
 *
 * @author dbarashev (Dmitry Barashev)
 */
public class GPSpreadsheetImportTest extends TestCase {

  private Supplier<InputStream> createSupplier(String data) {
    return createSupplier(data.getBytes(UTF_8));
  }

  private Supplier<InputStream> createSupplier(final byte[] data) {
    return () -> new ByteArrayInputStream(data);
  }

  private static Map<String, Task> buildTaskMap(TaskManager taskManager) {
    return Maps.uniqueIndex(Arrays.asList(taskManager.getTasks()), new Function<Task, String>() {
      @Override
      public String apply(Task input) {
        return input.getName();
      }
    });
  }

  private Map<String, HumanResource> buildResourceMap(HumanResourceManager resourceManager) {
    return Maps.uniqueIndex(resourceManager.getResources(), (HumanResource input) -> getName());
  }

  private static void assertDependency(Task dependant, Task dependee) {
    for (TaskDependency dep : dependant.getDependenciesAsDependant().toArray()) {
      if (dep.getDependee() == dependee) {
        return;
      }
    }
    fail("Can't find " + dependee + " in the list of predecessors of " + dependant);
  }

  private static void assertDependency(Task dependant, Task dependee, int lag) {
    for (TaskDependency dep : dependant.getDependenciesAsDependant().toArray()) {
      if (dep.getDependee() == dependee) {
        assertEquals("Unexpected lag value", lag, dep.getDifference());
        return;
      }
    }
    fail("Can't find " + dependee + " in the list of predecessors of " + dependant);
  }

  @Override
  protected void setUp() throws Exception {
    TaskDefaultColumn.setLocaleApi(new TaskDefaultColumn.LocaleApi() {
      @Override
      public String i18n(String key) {
        return GanttLanguage.getInstance().getText(key);
      }
    });
    GanttLanguage.getInstance().setShortDateFormat(new SimpleDateFormat("dd/MM/yy"));
  }

  public void testImportAssignments() throws Exception {
    String header1 = "Name,Begin date,End date,Resources,Duration,Completion,Web Link,Notes,Predecessors,ID";
    String data1 = "t1,23/07/12,25/07/12,Joe;John,,,,,,";

    String header2 = "Name,ID,e-mail,Phone,Default role";
    String data2 = "Joe,1,,,\nJohn,2,,,\nJack,3,,,";

    TaskManagerBuilder builder = TestSetupHelper.newTaskManagerBuilder();
    TaskManager taskManager = builder.build();
    HumanResourceManager resourceManager = builder.getResourceManager();
    RoleManager roleManager = new RoleManagerImpl();
    GanttCSVOpen importer = new GanttCSVOpen(createSupplier(Joiner.on('\n').join(header1, data1, "", header2, data2)),
        taskManager, resourceManager, roleManager, builder.getTimeUnitStack(), CSV);
    importer.load();
    Map<String, Task> taskMap = buildTaskMap(taskManager);
    Task t1 = taskMap.get("t1");
    assertNotNull(t1);
    Map<String, HumanResource> resourceMap = buildResourceMap(resourceManager);
    assertNotNull(t1.getAssignmentCollection().getAssignment(resourceMap.get("Joe")));
    assertNotNull(t1.getAssignmentCollection().getAssignment(resourceMap.get("John")));

    builder = TestSetupHelper.newTaskManagerBuilder();
    taskManager = builder.build();
    resourceManager = builder.getResourceManager();
    roleManager = new RoleManagerImpl();
    importer = new GanttCSVOpen(createSupplier(createXls(header1, data1, "", header2, data2)),
        taskManager, resourceManager, roleManager, builder.getTimeUnitStack(), XLS);
    importer.load();
    taskMap = buildTaskMap(taskManager);
    t1 = taskMap.get("t1");
    assertNotNull(t1);
    resourceMap = buildResourceMap(resourceManager);
    assertNotNull(t1.getAssignmentCollection().getAssignment(resourceMap.get("Joe")));
    assertNotNull(t1.getAssignmentCollection().getAssignment(resourceMap.get("John")));
  }

  public void testImportResourceRole() throws Exception {
    String header1 = "Name,Begin date,End date,Resources,Duration,Completion,Web Link,Notes,Predecessors,ID";
    String data1 = "";

    String header2 = "Name,ID,Default role";
    String data2 = "Joe,1,Default:1";

    TaskManagerBuilder builder = TestSetupHelper.newTaskManagerBuilder();
    TaskManager taskManager = builder.build();
    HumanResourceManager resourceManager = builder.getResourceManager();
    RoleManager roleManager = new RoleManagerImpl();
    GanttCSVOpen importer = new GanttCSVOpen(createSupplier(Joiner.on('\n').join(header1, data1, "", header2, data2)),
        taskManager, resourceManager, roleManager, builder.getTimeUnitStack(), CSV);
    importer.load();
    Map<String, HumanResource> resourceMap = buildResourceMap(resourceManager);
    assertEquals("project manager", resourceMap.get("Joe").getRole().getName());

    builder = TestSetupHelper.newTaskManagerBuilder();
    taskManager = builder.build();
    resourceManager = builder.getResourceManager();
    roleManager = new RoleManagerImpl();
    importer = new GanttCSVOpen(createSupplier(createXls(header1, data1, "", header2, data2)),
        taskManager, resourceManager, roleManager, builder.getTimeUnitStack(), XLS);
    importer.load();
    resourceMap = buildResourceMap(resourceManager);
    assertEquals("project manager", resourceMap.get("Joe").getRole().getName());
  }

  public void testCustomFields() throws Exception {
    String header1 = "Field1,ID,Name,Begin date,End date,Predecessors,Resources,Duration,Completion,Web Link,Notes,Field2";
    String data1 = "value1,,t1,23/07/12,25/07/12,,,,,,,value2";

    TaskManagerBuilder builder = TestSetupHelper.newTaskManagerBuilder();
    TaskManager taskManager = builder.build();
    GanttCSVOpen importer = new GanttCSVOpen(createSupplier(Joiner.on('\n').join(header1, data1)),
        taskManager, null, null, builder.getTimeUnitStack(), CSV);
    importer.load();
    Map<String, Task> taskMap = buildTaskMap(taskManager);
    Task t1 = taskMap.get("t1");
    assertNotNull(t1);

    CustomPropertyDefinition def1 = taskManager.getCustomPropertyManager().getCustomPropertyDefinition("Field1");
    CustomPropertyDefinition def2 = taskManager.getCustomPropertyManager().getCustomPropertyDefinition("Field2");
    assertNotNull(def1);
    assertNotNull(def2);

    assertEquals("value1", t1.getCustomValues().getValue(def1));
    assertEquals("value2", t1.getCustomValues().getValue(def2));

    builder = TestSetupHelper.newTaskManagerBuilder();
    taskManager = builder.build();
    importer = new GanttCSVOpen(createSupplier(createXls(header1, data1)),
        taskManager, null, null, builder.getTimeUnitStack(), XLS);
    importer.load();
    taskMap = buildTaskMap(taskManager);
    t1 = taskMap.get("t1");
    assertNotNull(t1);

    def1 = taskManager.getCustomPropertyManager().getCustomPropertyDefinition("Field1");
    def2 = taskManager.getCustomPropertyManager().getCustomPropertyDefinition("Field2");
    assertNotNull(def1);
    assertNotNull(def2);

    assertEquals("value1", t1.getCustomValues().getValue(def1));
    assertEquals("value2", t1.getCustomValues().getValue(def2));
  }

  public void testDependencies() throws Exception {
    String header1 = "ID,Name,Begin date,End date,Resources,Duration,Completion,Web Link,Notes,Predecessors";
    String data1 = "1,t1,23/07/12,25/07/12,,,,,,";
    String data2 = "2,t2,26/07/12,27/07/12,,,,,,1";
    String data3 = "3,t3,26/07/12,30/07/12,,,,,,1";
    String data4 = "4,t4,26/07/12,30/07/12,,,,,,1-FS=P1D";
    String data5 = "5,t5,26/07/12,30/07/12,,,,,,1-FS=P-1D";

    TaskManagerBuilder builder = TestSetupHelper.newTaskManagerBuilder();
    TaskManager taskManager = builder.build();
    GanttCSVOpen importer = new GanttCSVOpen(createSupplier(Joiner.on('\n').join(header1, data1, data2, data3, data4, data5)),
        taskManager, null, null, builder.getTimeUnitStack(), CSV);
    importer.load();
    Map<String, Task> taskMap = buildTaskMap(taskManager);
    Task t1 = taskMap.get("t1");
    Task t2 = taskMap.get("t2");
    Task t3 = taskMap.get("t3");
    Task t4 = taskMap.get("t4");
    Task t5 = taskMap.get("t5");

    assertDependency(t2, t1);
    assertDependency(t3, t1);
    assertDependency(t4, t1, 1);
    assertDependency(t5, t1, -1);

    builder = TestSetupHelper.newTaskManagerBuilder();
    taskManager = builder.build();
    importer = new GanttCSVOpen(createSupplier(createXls(header1, data1, data2, data3, data4, data5)),
        taskManager, null, null, builder.getTimeUnitStack(), XLS);
    importer.load();
    taskMap = buildTaskMap(taskManager);
    t1 = taskMap.get("t1");
    t2 = taskMap.get("t2");
    t3 = taskMap.get("t3");
    t4 = taskMap.get("t4");
    t5 = taskMap.get("t5");

    assertDependency(t2, t1);
    assertDependency(t3, t1);
    assertDependency(t4, t1, 1);
    assertDependency(t5, t1, -1);
  }

  public void testMilestone() throws Exception {
    String header1 = "ID,Name,Begin date,End date,Duration,Resources,Completion,Web Link,Notes,Predecessors";
    String data1 = "1,t1,23/07/12,24/07/12,1,,,,,";
    String data2 = "2,t2,26/07/12,26/07/12,0,,,,,";

    TaskManagerBuilder builder = TestSetupHelper.newTaskManagerBuilder();
    TaskManager taskManager = builder.build();
    GanttCSVOpen importer = new GanttCSVOpen(createSupplier(Joiner.on('\n').join(header1, data1, data2)),
        taskManager, null, null, builder.getTimeUnitStack(), CSV);
    importer.load();
    Map<String, Task> taskMap = buildTaskMap(taskManager);
    Task t1 = taskMap.get("t1");
    Task t2 = taskMap.get("t2");
    assertFalse(t1.isMilestone());
    assertTrue(t2.isMilestone());

    builder = TestSetupHelper.newTaskManagerBuilder();
    taskManager = builder.build();
    importer = new GanttCSVOpen(createSupplier(createXls(header1, data1, data2)),
        taskManager, null, null, builder.getTimeUnitStack(), XLS);
    importer.load();
    taskMap = buildTaskMap(taskManager);
    t1 = taskMap.get("t1");
    t2 = taskMap.get("t2");
    assertFalse(t1.isMilestone());
    assertTrue(t2.isMilestone());
  }

  public void testHierarchy() throws Exception {
    String header1 = "ID,Name,Begin date,End date,Duration,Outline number";
    String data1 = "1,t1,23/07/12,26/07/12,1,1";
    String data2 = "2,t2,23/07/12,24/07/12,1,1.1";
    String data3 = "3,t3,24/07/12,26/07/12,1,1.2";
    String data4 = "4,t4,24/07/12,25/07/12,1,1.2.1";
    String data5 = "5,t5,25/07/12,26/07/12,1,1.2.2";

    TaskManagerBuilder builder = TestSetupHelper.newTaskManagerBuilder();
    TaskManager taskManager = builder.build();
    GanttCSVOpen importer = new GanttCSVOpen(createSupplier(Joiner.on('\n').join(header1, data1, data2, data3, data4, data5)),
        taskManager, null, null, builder.getTimeUnitStack(), CSV);
    importer.load();
    Map<String, Task> taskMap = buildTaskMap(taskManager);
    TaskContainmentHierarchyFacade hierarchy = taskManager.getTaskHierarchy();
    Task t1 = taskMap.get("t1");
    Task t2 = taskMap.get("t2");
    Task t3 = taskMap.get("t3");
    Task t4 = taskMap.get("t4");
    Task t5 = taskMap.get("t5");
    assertEquals(t3, hierarchy.getContainer(t5));
    assertEquals(t3, hierarchy.getContainer(t4));
    assertEquals(t1, hierarchy.getContainer(t3));
    assertEquals(t1, hierarchy.getContainer(t2));

    builder = TestSetupHelper.newTaskManagerBuilder();
    taskManager = builder.build();
    importer = new GanttCSVOpen(createSupplier(createXls(header1, data1, data2, data3, data4, data5)),
        taskManager, null, null, builder.getTimeUnitStack(), XLS);
    importer.load();
    taskMap = buildTaskMap(taskManager);
    hierarchy = taskManager.getTaskHierarchy();
    t1 = taskMap.get("t1");
    t2 = taskMap.get("t2");
    t3 = taskMap.get("t3");
    t4 = taskMap.get("t4");
    t5 = taskMap.get("t5");
    assertEquals(t3, hierarchy.getContainer(t5));
    assertEquals(t3, hierarchy.getContainer(t4));
    assertEquals(t1, hierarchy.getContainer(t3));
    assertEquals(t1, hierarchy.getContainer(t2));
  }

  public void testUseEndDateInsteadOfDuration() throws Exception {
    String header1 = "ID,Name,Begin date,End date";
    String data1 = "1,t1,23/07/12,26/07/12";

    TaskManagerBuilder builder = TestSetupHelper.newTaskManagerBuilder();
    TaskManager taskManager = builder.build();
    GanttCSVOpen importer = new GanttCSVOpen(createSupplier(Joiner.on('\n').join(header1, data1)),
        taskManager, null, null, builder.getTimeUnitStack(), CSV);
    importer.load();
    Map<String, Task> taskMap = buildTaskMap(taskManager);
    assertEquals(4.0f, taskMap.get("t1").getDuration().getLength(builder.getTimeUnitStack().getDefaultTimeUnit()));

    builder = TestSetupHelper.newTaskManagerBuilder();
    taskManager = builder.build();
    importer = new GanttCSVOpen(createSupplier(createXls(header1, data1)),
        taskManager, null, null, builder.getTimeUnitStack(), XLS);
    importer.load();
    taskMap = buildTaskMap(taskManager);
    assertEquals(4.0f, taskMap.get("t1").getDuration().getLength(builder.getTimeUnitStack().getDefaultTimeUnit()));
  }

  private static void assertOrder(String first, String second) {
    assertEquals(-1, TaskRecords.OUTLINE_NUMBER_COMPARATOR.compare(first, second));
    assertEquals(1, TaskRecords.OUTLINE_NUMBER_COMPARATOR.compare(second, first));
    assertEquals(0, TaskRecords.OUTLINE_NUMBER_COMPARATOR.compare(first, first));
    assertEquals(0, TaskRecords.OUTLINE_NUMBER_COMPARATOR.compare(second, second));
  }

  public void testOutlineNumberComparator() {
    assertOrder("1", "2");
    assertOrder("1", "1.1");
    assertOrder("1.1", "1.2");
    assertOrder("1.1", "2");
    assertOrder("1.2", "1.10");
    assertOrder("2", "10");
  }

  private byte[] createXls(String... rows) throws Exception {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try (SpreadsheetWriter writer = new XlsWriterImpl(stream)) {
      for (String row : rows) {
        for (String cel : row.split(",")) {
          writer.print(cel.trim());
        }
        writer.println();
      }
    }
    return stream.toByteArray();
  }
}