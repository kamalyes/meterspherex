<template>
  <div>
    <ms-module-minder
      v-loading="result.loading"
      minder-key="TEST_CASE"
      :tree-nodes="treeNodes"
      :tags="tags"
      :select-node="selectNode"
      :distinct-tags="tags"
      :module-disable="false"
      :show-module-tag="true"
      :move-enable="moveEnable"
      :tag-edit-check="tagEditCheck()"
      :priority-disable-check="priorityDisableCheck()"
      :disabled="disabled"
      :get-extra-node-count="getMinderTreeExtraNodeCount()"
      @afterMount="handleAfterMount"
      @save="save"
      ref="minder"
    />

    <IssueRelateList
      :case-id="getCurCaseId()"
      @refresh="refreshRelateIssue"
      ref="issueRelate"/>

    <test-plan-issue-edit
      :is-minder="true"
      :plan-id="null"
      :case-id="getCurCaseId()"
      @refresh="refreshIssue"
      ref="issueEdit"/>

    <is-change-confirm
      @confirm="changeConfirm"
      ref="isChangeConfirm"/>

  </div>

</template>

<script>
import {
  getChildNodeId,
  handleAfterSave,
  handleExpandToLevel,
  handleMinderIssueDelete,
  handleTestCaseAdd,
  handTestCaeEdit,
  isCaseNodeData,
  isModuleNode,
  isModuleNodeData,
  listenBeforeExecCommand,
  listenDblclick,
  listenNodeSelected,
  loadSelectNodes,
  priorityDisableCheck,
  tagEditCheck,
} from "@/business/common/minder/minderUtils";
import {getNodePath, getUUID} from "metersphere-frontend/src/utils";
import {hasPermission} from "metersphere-frontend/src/utils/permission";
import {
  getTestCasesForMinder,
  getMinderExtraNode,
  getMinderTreeExtraNodeCount,
  testCaseMinderEdit
} from "@/api/testCase";
import {addIssueHotBox, getSelectedNodeData, handleIssueAdd, handleIssueBatch} from "./minderUtils";
import IssueRelateList from "@/business/case/components/IssueRelateList";
import TestPlanIssueEdit from "@/business/case/components/TestPlanIssueEdit";
import {setPriorityView} from "vue-minder-editor-plus/src/script/tool/utils";
import IsChangeConfirm from "metersphere-frontend/src/components/IsChangeConfirm";
import MsModuleMinder from "@/business/common/minder/MsModuleMinder";

import {useStore} from "@/store"
import {mapState} from "pinia";
import {getCurrentWorkspaceId} from "@/business/utils/sdk-utils";
import {getIssuesForMinder} from "@/api/issue";


export default {
  name: "TestCaseMinder",
  components: {MsModuleMinder, IsChangeConfirm, TestPlanIssueEdit, IssueRelateList},
  data() {
    return {
      testCase: [],
      dataMap: new Map(),
      tags: [this.$t('api_test.definition.request.case'), this.$t('test_track.case.prerequisite'), this.$t('commons.remark'), this.$t('test_track.module.module')],
      result: {loading: false},
      needRefresh: false,
      noRefresh: false,
      noRefreshMinder: false,
      saveCases: [],
      saveModules: [],
      saveModuleNodeMap: new Map(),
      deleteNodes: [], // ??????????????????????????????????????????
      saveExtraNode: {},
      extraNodeChanged: [] // ??????????????????????????????????????????
    }
  },
  props: {
    treeNodes: {
      type: Array,
      default() {
        return []
      }
    },
    currentVersion: String,
    condition: Object,
    projectId: String,
    activeName: String
  },
  computed: {
    ...mapState(useStore, {
      selectNodeIds: 'testCaseSelectNodeIds',
      selectNode: 'testCaseSelectNode',
      moduleOptions: 'testCaseModuleOptions',
      testCaseDefaultValue: 'testCaseDefaultValue',
    }),
    disabled() {
      return !hasPermission('PROJECT_TRACK_CASE:READ+EDIT');
    },
    moveEnable() {
      // ???????????????????????????????????????????????????
      return !this.condition.orders || this.condition.orders.length < 1;
    },
    workspaceId() {
      return getCurrentWorkspaceId();
    }

  },
  watch: {
    selectNode() {
      if (this.$refs.minder) {
        this.$refs.minder.handleNodeSelect(this.selectNode);
      }
    },
    currentVersion() {
      this.$refs.minder.initData();
    },
    treeNodes(newVal, oldVal) {
      if (newVal !== oldVal && this.activeName === 'default') {
        // ???????????????????????? tab ?????????????????????????????????????????????
        this.handleNodeUpdateForMinder();
      }
    }
  },
  mounted() {
    this.setIsChange(false);
    if (this.selectNode && this.selectNode.data) {
      if (this.$refs.minder) {
        let importJson = this.$refs.minder.getImportJsonBySelectNode(this.selectNode.data);
        this.$refs.minder.setJsonImport(importJson);
      }
    }
  },
  methods: {
    handleNodeUpdateForMinder() {
      if (this.noRefreshMinder) {
        // ?????????????????????????????????????????????????????????
        this.noRefreshMinder = false;
        return;
      }
      this.noRefresh = true;
      // ??????????????????????????????????????????????????????
      if (!useStore().isTestCaseMinderChanged) {
        if (this.$refs.minder) {
          this.$refs.minder.initData();
        }
      } else {
        this.$refs.isChangeConfirm.open();
      }
    },
    changeConfirm(isSave) {
      if (isSave) {
        this.save(() => {
          this.initData();
        });
      } else {
        this.initData();
      }
    },
    initData() {
      if (this.$refs.minder) {
        this.$refs.minder.initData();
      }
    },
    getMinderTreeExtraNodeCount() {
      return getMinderTreeExtraNodeCount;
    },
    handleAfterMount() {
      listenNodeSelected(() => {
        // ???????????????????????????????????????
        loadSelectNodes(this.getParam(), getTestCasesForMinder, null, getMinderExtraNode);
      });

      listenDblclick(() => {
        let minder = window.minder;
        let selectNodes = minder.getSelectedNodes();
        let isNotDisableNode = false;
        selectNodes.forEach(node => {
          // ????????????????????????????????????????????????????????????
          if (!node.data.disable) {
            isNotDisableNode = true;
          }
          if (node.data.type === 'issue') {
            getIssuesForMinder(node.data.id, this.projectId, this.workspaceId)
              .then((r) => {
                let data = r.data;
                this.$refs.issueEdit.open(data);
              })
          }
        });
        if (isNotDisableNode) {
          this.setIsChange(true);
        }
      });

      listenBeforeExecCommand((even) => {
        if (even.commandName === 'expandtolevel') {
          let level = Number.parseInt(even.commandArgs);
          handleExpandToLevel(level, even.minder.getRoot(), this.getParam(), getTestCasesForMinder, null, getMinderExtraNode);
        }

        if (handleMinderIssueDelete(even.commandName)) return; // ???????????????????????????????????????

        if (['priority', 'resource', 'removenode', 'appendchildnode', 'appendparentnode', 'appendsiblingnode', 'paste'].indexOf(even.commandName) > -1) {
          // ??????????????????????????????
          this.setIsChange(true);
        }

        if ('removenode' === even.commandName) {
          let nodes = window.minder.getSelectedNodes();
          if (nodes) {
            nodes.forEach((node) => {
              if (isModuleNodeData(node.data) && node.children && node.children.length > 0) {
                this.$warning('?????????????????????????????????????????????');
              }
            });
          }
        }

        if ('resource' === even.commandName) {
          // ???????????????????????????????????????????????????????????????
          setTimeout(() => setPriorityView(true, 'P'), 100);
        }
      });

      addIssueHotBox(this);
    },
    getParam() {
      return {
        request: {
          projectId: this.projectId,
          versionId: this.currentVersion,
          orders: this.condition.orders
        },
        result: this.result,
        isDisable: false
      }
    },
    setIsChange(isChanged) {
      useStore().$patch({
        isTestCaseMinderChanged: isChanged
      });
    },
    save(callback) {
      this.saveCases = [];
      this.saveModules = [];
      this.deleteNodes = []; // ??????????????????????????????????????????
      this.saveExtraNode = {};
      this.saveModuleNodeMap = new Map();
      this.buildSaveParam(window.minder.getRoot());

      this.saveModules.forEach(module => {
        let nodeIds = [];
        getChildNodeId(this.saveModuleNodeMap.get(module.id), nodeIds);
        module.nodeIds = nodeIds;
      });

      let param = {
        projectId: this.projectId,
        data: this.saveCases,
        ids: this.deleteNodes.map(item => item.id),
        testCaseNodes: this.saveModules,
        extraNodeRequest: {
          groupId: this.projectId,
          type: "TEST_CASE",
          data: this.saveExtraNode,
        }
      }
      this.result.loading = true;
      testCaseMinderEdit(param)
        .then(() => {
          this.result.loading = false;
          this.$success(this.$t('commons.save_success'));
          handleAfterSave(window.minder.getRoot());
          this.extraNodeChanged.forEach(item => {
            item.isExtraNode = false;
          });
          this.extraNodeChanged = [];
          if (!this.noRefresh) {
            this.$emit('refresh');
            // ??????????????????????????????????????????????????????????????????
            // ?????????????????????????????????????????????????????????
            this.noRefreshMinder = true;
          }
          // ???????????????????????????????????????????????????
          this.noRefresh = false;
          this.setIsChange(false);
          if (callback && callback instanceof Function) {
            callback();
          }
        });
    },
    buildSaveParam(root, parent, preNode, nextNode) {
      let data = root.data;
      if (isCaseNodeData(data)) {
        this.buildSaveCase(root, parent, preNode, nextNode);
      } else {
        let deleteChild = data.deleteChild;
        if (deleteChild && deleteChild.length > 0 && isModuleNodeData(data)) {
          this.deleteNodes.push(...deleteChild);
        }

        if (data.type !== 'tmp' && data.changed) {
          if (isModuleNodeData(data)) {
            if (data.contextChanged) {
              this.buildSaveModules(root, data, parent);
              root.children && root.children.forEach(i => {
                if (isModuleNode(i)) {
                  i.data.changed = true;
                  i.data.contextChanged = true; // ????????????????????????????????????????????????????????????level?????????????????????
                }
              });
            }
          } else {
            // ??????????????????
            this.buildExtraNode(data, parent, root);
          }
        }

        if (root.children) {
          for (let i = 0; i < root.children.length; i++) {
            let childNode = root.children[i];
            let preNodeTmp = null;
            let nextNodeTmp = null;
            if (i != 0) {
              preNodeTmp = root.children[i - 1];
            }
            if (i + 1 < root.children.length) {
              nextNodeTmp = root.children[i + 1];
            }
            this.buildSaveParam(childNode, root.data, preNodeTmp, nextNodeTmp);
          }
        }
      }
    },
    buildSaveModules(node, data, parent) {
      if (!data.text) {
        return;
      }
      let pId = parent ? (parent.newId ? parent.newId : parent.id) : null;

      if (parent && !isModuleNodeData(parent)) {
        this.throwError(this.$t('test_track.case.minder_not_module_tip', [data.text]));
      }

      let module = {
        id: data.id,
        name: data.text,
        level: parent ? parent.level + 1 : data.level,
        parentId: pId
      };
      data.level = module.level;

      if (data.isExtraNode) {
        // ????????????????????????????????????????????????????????????????????????????????????
        this.pushDeleteNode(data);
        module.id = null;
        this.extraNodeChanged.push(data);
      }

      if (data.type === 'case') {
        // ??????????????????????????????????????????????????????????????????????????????
        this.pushDeleteNode(data);
        module.id = null;
      }

      if (module.id && module.id.length > 20) {
        module.isEdit = true; // ??????
      } else {
        module.isEdit = false; // ??????
        module.id = getUUID();
        data.newId = module.id;
        this.moduleOptions.push({id: data.newId, path: getNodePath(pId, this.moduleOptions) + '/' + module.name});
      }

      this.saveModuleNodeMap.set(module.id, node);

      if (module.level > 8) {
        this.throwError(this.$t('commons.module_deep_limit'));
      }
      this.saveModules.push(module);
    },
    buildExtraNode(data, parent, root) {
      if (data.type !== 'node' && data.type !== 'tmp' && parent && isModuleNodeData(parent) && data.changed === true) {
        // ?????????????????????????????????????????????????????????
        let pId = parent.newId ? parent.newId : parent.id;
        let nodes = this.saveExtraNode[pId];
        if (!nodes) {
          nodes = [];
        }
        nodes.push(JSON.stringify(this._buildExtraNode(root)));
        this.saveExtraNode[pId] = nodes;
      }
    },
    validate(parent, data) {
      if (parent.id === 'root') {
        this.throwError(this.$t('test_track.case.minder_all_module_tip'));
      }

      if (parent.isExtraNode && !isModuleNodeData(parent)) {
        this.throwError(this.$t('test_track.case.minder_tem_node_tip', [parent.text]));
      }

      if (data.type === 'node') {
        this.throwError(this.$t('test_track.case.minder_is_module_tip', [data.text]));
      }
    },
    buildSaveCase(node, parent, preNode, nextNode) {
      let data = node.data;
      if (!data.text) {
        return;
      }

      this.validate(parent, data);

      let isChange = false;

      let nodeId = parent ? (parent.newId ? parent.newId : parent.id) : "";
      let priorityDefaultValue;
      if (data.priority) {
        priorityDefaultValue = 'P' + (data.priority - 1);
      } else {
        priorityDefaultValue = this.testCaseDefaultValue['????????????'] ? this.testCaseDefaultValue['????????????'] : 'P' + 0;
      }

      let testCase = {
        id: data.id,
        name: data.text,
        nodeId: nodeId,
        nodePath: getNodePath(nodeId, this.moduleOptions),
        type: data.type ? data.type : 'functional',
        method: data.method ? data.method : 'manual',
        maintainer: this.testCaseDefaultValue['?????????'] ? this.testCaseDefaultValue['?????????'] : data.maintainer,
        priority: priorityDefaultValue,
        prerequisite: "",
        remark: "",
        stepDescription: "",
        expectedResult: "",
        status: this.testCaseDefaultValue['????????????'] ? this.testCaseDefaultValue['????????????'] : 'Prepare',
        steps: [{
          num: 1,
          desc: '',
          result: ''
        }],
        tags: "[]",
        stepModel: "STEP"
      };
      if (data.changed) isChange = true;
      let steps = [];
      let stepNum = 1;
      if (node.children) {
        let prerequisiteNodes = node.children.filter(childNode => childNode.data.resource && childNode.data.resource.indexOf(this.$t('test_track.case.prerequisite')) > -1);
        if (prerequisiteNodes.length > 1) {
          this.throwError('[' + testCase.name + ']' + this.$t('test_track.case.exists_multiple_prerequisite_node'));
        }
        let remarkNodes = node.children.filter(childNode => childNode.data.resource && childNode.data.resource.indexOf(this.$t('commons.remark')) > -1);
        if (remarkNodes.length > 1) {
          this.throwError('[' + testCase.name + ']' + this.$t('test_track.case.exists_multiple_remark_node'));
        }
        node.children.forEach((childNode) => {
          let childData = childNode.data;
          if (childData.type === 'issue') return;
          if (childData.resource && childData.resource.indexOf(this.$t('test_track.case.prerequisite')) > -1) {
            testCase.prerequisite = childData.text;
            if (childNode.children && childNode.children.length > 0) {
              this.throwError('[' + testCase.name + ']???????????????????????????????????????');
            }
          } else if (childData.resource && childData.resource.indexOf(this.$t('commons.remark')) > -1) {
            testCase.remark = childData.text;
            if (childNode.children && childNode.children.length > 0) {
              this.throwError('[' + testCase.name + ']?????????????????????????????????');
            }
          } else {
            // ????????????
            let step = {};
            step.num = stepNum++;
            step.desc = childData.text;
            if (childNode.children) {
              let result = "";
              childNode.children.forEach((child) => {
                result += child.data.text;
                if (child.data.changed) {
                  isChange = true;
                }
              })
              step.result = result;
            }
            steps.push(step);

            if (data.stepModel === 'TEXT') {
              testCase.stepDescription = step.desc;
              testCase.expectedResult = step.result;
            }
          }
          if (childData.changed) {
            isChange = true;
          }

          childNode.children.forEach((child) => {
            if (child.children && child.children.length > 0) {
              this.throwError('[' + testCase.name + ']???????????????????????????????????????');
            }
          });
        })
      }
      testCase.steps = JSON.stringify(steps);

      if (data.isExtraNode) {
        // ??????????????????????????????????????????????????????????????????????????????????????????
        this.pushDeleteNode(data);
        testCase.id = null;
        this.extraNodeChanged.push(data);
      }

      if (isChange) {

        testCase.targetId = null; // ????????????

        if (this.moveEnable) {
          if (this.isCaseNode(preNode)) {
            let preId = preNode.data.id;
            if (preId && preId.length > 15) {
              testCase.targetId = preId;
              testCase.moveMode = 'AFTER';
            } else {
              testCase.moveMode = 'APPEND';
            }
          } else if (this.isCaseNode(nextNode) && !nextNode.data.isExtraNode) {
            let nextId = nextNode.data.id;
            if (nextId && nextId.length > 15) {
              testCase.targetId = nextId;
              testCase.moveMode = 'BEFORE';
            }
          }
        }

        if (testCase.id && testCase.id.length > 20) {
          testCase.isEdit = true; // ??????
        } else {
          testCase.isEdit = false; // ??????
          testCase.id = getUUID();
          data.newId = testCase.id;
        }
        this.saveCases.push(testCase);
      }
      if (testCase.nodeId !== 'root' && testCase.nodeId.length < 15) {
        this.throwError(this.$t('test_track.case.create_case') + "'" + testCase.name + "'" + this.$t('test_track.case.minder_create_tip'));
      }
    },
    pushDeleteNode(data) {
      // ??????????????????????????????????????????????????????????????????????????????????????????
      let deleteData = {};
      Object.assign(deleteData, data);
      this.deleteNodes.push(deleteData);
      data.id = "";
    },
    isCaseNode(node) {
      if (node && node.data.resource && node.data.resource.indexOf(this.$t('api_test.definition.request.case')) > -1) {
        return true;
      }
      return false;
    },
    _buildExtraNode(node) {
      let data = node.data;
      let nodeData = {
        text: data.text,
        id: data.id,
        resource: data.resource,
      };
      if (nodeData.id && nodeData.id.length > 20) {
        nodeData.isEdit = true; // ??????
      } else {
        nodeData.isEdit = false; // ??????
        nodeData.id = getUUID();
        data.newId = nodeData.id;
      }
      if (node.children) {
        nodeData.children = [];
        node.children.forEach(item => {
          nodeData.children.push(this._buildExtraNode(item));
        });
      }
      data.isExtraNode = true;
      return nodeData;
    },
    throwError(tip) {
      this.$error(tip)
      throw new Error(tip);
    },
    tagEditCheck() {
      return tagEditCheck;
    },
    priorityDisableCheck() {
      return priorityDisableCheck;
    },
    // ??????????????????????????????????????????tab???????????????????????????
    addCase(data, type) {
      if (type === 'edit') {
        handTestCaeEdit(data);
      } else {
        handleTestCaseAdd(data.nodeId, data);
      }
      this.needRefresh = true;
    },
    refresh() {
      // ??????tab????????????????????????????????????????????????
      if (this.needRefresh) {
        let jsonImport = window.minder.exportJson();
        this.$refs.minder.setJsonImport(jsonImport);
        this.$nextTick(() => {
          if (this.$refs.minder) {
            this.$refs.minder.reload();
          }
        });
        this.needRefresh = false;
      }
    },
    getCurCaseId() {
      return getSelectedNodeData().id;
    },
    refreshIssue(issue) {
      handleIssueAdd(issue);
    },
    refreshRelateIssue(issues) {
      handleIssueBatch(issues);
    }
  }
}
</script>

<style scoped>

</style>
