<template>
  <div v-loading="loading">
    <el-form :model="form" size="small" :rules="rules" :disabled="show" ref="form">
      <el-form-item :label="$t('ldap.url')" prop="url">
        <el-input v-model="form.url" :placeholder="$t('ldap.input_url_placeholder')"/>
      </el-form-item>
      <el-form-item :label="$t('ldap.dn')" prop="dn">
        <el-input v-model="form.dn" :placeholder="$t('ldap.input_dn')"/>
      </el-form-item>
      <el-form-item :label="$t('ldap.password')" prop="password">
        <el-input v-model="form.password" :placeholder="$t('ldap.input_password')" show-password
                  auto-complete="new-password"/>
      </el-form-item>
      <el-form-item :label="$t('ldap.ou')" prop="ou">
        <el-input v-model="form.ou" :placeholder="$t('ldap.input_ou_placeholder')"/>
      </el-form-item>
      <el-form-item :label="$t('ldap.filter')" prop="filter">
        <el-input v-model="form.filter" :placeholder="$t('ldap.input_filter_placeholder')"/>
      </el-form-item>
      <el-form-item :label="$t('ldap.mapping')" prop="mapping">
        <el-input v-model="form.mapping" :placeholder="$t('ldap.input_mapping_placeholder')"/>
      </el-form-item>
      <el-form-item :label="$t('ldap.open')" prop="open">
        <el-checkbox v-model="form.open"/>
      </el-form-item>
    </el-form>

    <div>
      <el-button type="primary" size="small" :disabled="!show" @click="testConnection">{{ $t('ldap.test_connect') }}
      </el-button>
      <el-button type="primary" size="small" :disabled="!showLogin || !show" @click="testLogin">
        {{ $t('ldap.test_login') }}
      </el-button>
      <el-button v-if="showEdit" size="small" @click="edit" v-permission="['SYSTEM_SETTING:READ+EDIT']">{{ $t('ldap.edit') }}</el-button>
      <el-button type="success" v-if="showSave" size="small" @click="save('form')">{{ $t('commons.save') }}
      </el-button>
      <el-button type="info" v-if="showCancel" size="small" @click="cancel">{{ $t('commons.cancel') }}</el-button>
    </div>

    <el-dialog :title="$t('ldap.test_login')" :visible.sync="loginVisible" width="30%" destroy-on-close
               v-loading="loading" @close="close">
      <el-form :model="loginForm" :rules="loginFormRules" ref="loginForm" label-width="90px">
        <el-form-item :label="$t('commons.username')" prop="username">
          <el-input v-model="loginForm.username" autocomplete="off" :placeholder="$t('ldap.input_username')"/>
        </el-form-item>
        <el-form-item :label="$t('commons.password')" prop="password">
          <el-input v-model="loginForm.password" autocomplete="new-password" :placeholder="$t('ldap.input_password')"
                    show-password/>
        </el-form-item>
      </el-form>
      <span slot="footer">
           <ms-dialog-footer
             @cancel="loginVisible = false"
             @confirm="login('loginForm')"/>
        </span>
    </el-dialog>

  </div>
</template>

<script>
import MsDialogFooter from "metersphere-frontend/src/components/MsDialogFooter";
import {listenGoBack, removeGoBackListener} from "metersphere-frontend/src/utils";
import {publicKeyEncrypt} from "metersphere-frontend/src/utils/token";
import {getLdapInfo, saveLdapInfo, testLdapConnect, testLdapLogin} from "../../../api/system";

export default {
  name: "LdapSetting",
  components: {
    MsDialogFooter
  },
  data() {
    return {
      form: {open: false},
      loginForm: {},
      loading: false,
      show: true,
      showEdit: true,
      showSave: false,
      showCancel: false,
      showLogin: false,
      loginVisible: false,
      rules: {
        url: {required: true, message: this.$t('ldap.input_url'), trigger: ['change', 'blur']},
        dn: {required: true, message: this.$t('ldap.input_dn'), trigger: ['change', 'blur']},
        password: {required: true, message: this.$t('ldap.input_password'), trigger: ['change', 'blur']},
        ou: {required: true, message: this.$t('ldap.input_ou'), trigger: ['change', 'blur']},
        filter: {required: true, message: this.$t('ldap.input_filter'), trigger: ['change', 'blur']},
        mapping: {required: true, message: this.$t('ldap.input_mapping'), trigger: ['change', 'blur']}
      },
      loginFormRules: {
        username: {required: true, message: this.$t('ldap.input_username'), trigger: 'blur'},
        password: {required: true, message: this.$t('ldap.input_password'), trigger: 'blur'}
      }
    }
  },
  created() {
    this.init();
  },
  methods: {
    init() {
      this.loading = getLdapInfo().then(res => {
        this.form = res.data;
        this.form.open = this.form.open === 'true';
        this.$nextTick(() => {
          if (this.$refs.form) {
            this.$refs.form.clearValidate();
          }
        })
      });
    },
    edit() {
      this.show = false;
      this.showEdit = false;
      this.showSave = true;
      this.showCancel = true;
    },
    cancel() {
      this.showEdit = true;
      this.showCancel = false;
      this.showSave = false;
      this.show = true;
      this.init();
    },
    close() {
      removeGoBackListener(this.close);
      this.loginVisible = false;
    },
    testConnection() {
      if (!this.checkParam()) {
        return false;
      }
      this.loading = testLdapConnect(this.form).then(() => {
        this.$success(this.$t('commons.connection_successful'));
        this.showLogin = true;
      }).catch(() => {
        this.showLogin = false;
      });
    },
    testLogin() {
      if (!this.checkParam()) {
        return false;
      }

      if (!this.form.ou) {
        this.$warning(this.$t('ldap.ou_cannot_be_empty'));
        return false;
      }

      if (!this.form.filter) {
        this.$warning(this.$t('ldap.filter_cannot_be_empty'));
        return false;
      }

      if (!this.form.mapping) {
        this.$warning(this.$t('ldap.mapping_cannot_be_empty'));
        return false;
      }

      this.loginForm = {};
      this.loginVisible = true;
      listenGoBack(this.close);
    },
    checkParam() {
      if (!this.form.url) {
        this.$warning(this.$t('ldap.url_cannot_be_empty'));
        return false;
      }

      if (!this.form.dn) {
        this.$warning(this.$t('ldap.dn_cannot_be_empty'));
        return false;
      }

      if (!this.form.password) {
        this.$warning(this.$t('ldap.password_cannot_be_empty'));
        return false;
      }

      return true;
    },
    save(form) {

      let param = [
        {paramKey: "ldap.url", paramValue: this.form.url, type: "text", sort: 1},
        {paramKey: "ldap.dn", paramValue: this.form.dn, type: "text", sort: 2},
        {paramKey: "ldap.password", paramValue: this.form.password, type: "password", sort: 3},
        {paramKey: "ldap.ou", paramValue: this.form.ou, type: "text", sort: 4},
        {paramKey: "ldap.filter", paramValue: this.form.filter, type: "text", sort: 5},
        {paramKey: "ldap.mapping", paramValue: this.form.mapping, type: "text", sort: 6},
        {paramKey: "ldap.open", paramValue: this.form.open, type: "text", sort: 7}
      ];

      this.$refs[form].validate(valid => {
        if (valid) {
          this.loading = saveLdapInfo(param).then(() => {
            this.show = true;
            this.showEdit = true;
            this.showSave = false;
            this.showCancel = false;
            this.showLogin = false;
            this.$success(this.$t('commons.save_success'));
            this.init();
          });
        } else {
          return false;
        }
      })
    },
    login(form) {
      let publicKey = localStorage.getItem("publicKey");
      this.$refs[form].validate(valid => {
        if (!valid) {
          return false;
        }
        let form = {
          username: publicKeyEncrypt(this.loginForm.username, publicKey),
          password: publicKeyEncrypt(this.loginForm.password, publicKey),
        };
        this.loading = testLdapLogin(form).then(() => {
          this.$success(this.$t('ldap.login_success'));
        });
      })
    }
  }
}
</script>

<style scoped>

</style>
