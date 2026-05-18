<template>
  <GiPageLayout margin>
    <a-row justify="space-between" class="g-row-tool">
      <a-space wrap>
        <GiButton type="delete" @click="onBatchDelete" />
      </a-space>
      <a-space wrap>
        <a-select
          v-model="queryParams.userFilter"
          :options="userFilterOptions"
          placeholder="用户筛选"
          allow-clear
          style="width: 160px"
        />
        <a-input
          v-model="queryParams.username"
          placeholder="输入用户名搜索"
          allow-clear
          style="width: 200px"
        />
        <GiButton type="search" @click="search" />
        <GiButton type="reset" @click="reset" />
      </a-space>
    </a-row>

    <a-table
      class="g-table user-table"
      row-key="id"
      :loading="loading"
      :data="userList"
      :columns="tableColumns"
      :bordered="{ cell: true }"
      :scroll="{ x: 'max-content', minWidth: 1480 }"
      :pagination="pagination"
      :row-selection="{ type: 'checkbox', showCheckedAll: true }"
      :selected-keys="selectedKeys"
      @select="select"
      @select-all="selectAll"
    />

    <UserFormModal ref="UserFormModalRef" @save-success="search" />
    <UserDetailDrawer ref="UserDetailDrawerRef" />
  </GiPageLayout>
</template>

<script setup lang="tsx">
import type { TableColumnData } from '@arco-design/web-vue'
import type * as T from '@/apis/system/user'
import { Link, Message, Modal, Popconfirm, Space, Tag } from '@arco-design/web-vue'
import { baseAPI, setForumAdmin, setUserMute, updateUserRemark } from '@/apis/system/user'
import { useTable } from '@/hooks'
import { useUserStore } from '@/stores'
import { isVipActive, vipTierLabel } from '@/utils/vip'
import UserDetailDrawer from './UserDetailDrawer.vue'
import UserFormModal from './UserFormModal.vue'

defineOptions({ name: 'SystemUser' })

const adminUserStore = useUserStore()

const UserFormModalRef = useTemplateRef('UserFormModalRef')
const UserDetailDrawerRef = useTemplateRef('UserDetailDrawerRef')

const userFilterOptions = [
  { label: '会员用户', value: 'member' },
  { label: '普通用户', value: 'normal' },
  { label: '管理员', value: 'admin' },
  { label: '禁言用户', value: 'muted' },
  { label: '未禁言用户', value: 'unmuted' },
  { label: '已删除用户', value: 'deleted' },
]

const queryParams = reactive({
  username: '',
  userFilter: '' as string,
})

const { loading, tableData: userList, pagination, selectedKeys, search, select, selectAll, fixed, onDelete, onBatchDelete } = useTable({
  listAPI: page => baseAPI.getList({ ...page, ...queryParams }),
  deleteAPI: ids => baseAPI.delete({ ids }),
  immediate: true,
})

const reset = () => {
  queryParams.username = ''
  queryParams.userFilter = ''
  search()
}

const onEdit = (item: T.ListItem) => {
  UserFormModalRef.value?.edit(item.id)
}

const onDetail = (item: T.ListItem) => {
  UserDetailDrawerRef.value?.open(item.id)
}

/** 列表 status：1=正常 0=禁言 */
const isMuted = (u: T.ListItem) => u.status === '0'

async function onMuteChange(item: T.ListItem, muted: boolean) {
  await setUserMute({ id: item.id, muted })
  Message.success(muted ? '已禁言' : '已解禁')
  search()
}

function confirmMute(u: T.ListItem) {
  const muted = !isMuted(u)
  Modal.warning({
    title: muted ? '确认禁言' : '确认解禁',
    content: muted
      ? `确定对用户「${u.nickname || u.username}」禁言？禁言后将无法发帖与回复。`
      : `确定解除用户「${u.nickname || u.username}」的禁言？`,
    hideCancel: false,
    onBeforeOk: async () => {
      await onMuteChange(u, muted)
      return true
    },
  })
}

function confirmSetForumAdmin(u: T.ListItem) {
  Modal.warning({
    title: '设置为管理员',
    content: `确定将「${u.nickname || u.username}」设置为论坛管理员？该用户将获得管理后台访问权限。`,
    okText: '确认设置',
    okButtonProps: { status: 'success' },
    hideCancel: false,
    onBeforeOk: async () => {
      await setForumAdmin({ id: u.id, isAdmin: 1 })
      Message.success('已设置为管理员')
      search()
      return true
    },
  })
}

function confirmRemoveForumAdmin(u: T.ListItem) {
  Modal.warning({
    title: '解除管理员',
    content: `确定取消「${u.nickname || u.username}」的论坛管理员权限？`,
    okText: '确认解除',
    okButtonProps: { status: 'danger' },
    hideCancel: false,
    onBeforeOk: async () => {
      await setForumAdmin({ id: u.id, isAdmin: 0 })
      Message.success('已解除管理员')
      search()
      return true
    },
  })
}

async function saveRemark(u: T.ListItem, remark: string) {
  if (remark === (u.description || ''))
    return
  await updateUserRemark({ id: u.id, remark })
  u.description = remark
  Message.success('管理员标签已保存')
}

function renderUserType(u: T.ListItem) {
  return (
    <Space size={4}>
      {u.forumAdmin
        ? <Tag color="red">管理员</Tag>
        : isVipActive(u.vipTier, u.vipExpireAt)
          ? (
              <Tag color="gold">
                {vipTierLabel(u.vipTier) ? `会员用户(${vipTierLabel(u.vipTier)})` : '会员用户'}
              </Tag>
            )
          : <Tag>普通用户</Tag>}
      {isMuted(u) ? <Tag color="orangered">禁言</Tag> : null}
      {u.deleteState === 1 ? <Tag color="gray">已删除</Tag> : null}
    </Space>
  )
}

const tableColumns: TableColumnData[] = [
  {
    title: '序号',
    width: 68,
    align: 'center',
    render: ({ rowIndex }) => <span>{rowIndex + 1}</span>,
  },
  {
    title: '用户名',
    dataIndex: 'username',
    width: 120,
    render: ({ record }) => (
      <Link onClick={() => onDetail(record as T.ListItem)}>{record.username}</Link>
    ),
  },
  {
    title: '昵称',
    dataIndex: 'nickname',
    width: 140,
    render: ({ record }) => (
      <GiCellAvatar avatar={record.avatar} name={record.nickname} />
    ),
  },
  {
    title: '性别',
    dataIndex: 'gender',
    width: 72,
    align: 'center',
    render: ({ record }) => <GiCellGender gender={record.gender} />,
  },
  { title: '邮箱', dataIndex: 'email', width: 180, ellipsis: true, tooltip: true },
  { title: '手机号', dataIndex: 'phone', width: 130 },
  {
    title: '类型',
    width: 180,
    align: 'center',
    render: ({ record }) => renderUserType(record as T.ListItem),
  },
  {
    title: '管理员标签',
    dataIndex: 'description',
    width: 200,
    render: ({ record }) => {
      const u = record as T.ListItem
      return (
        <a-input
          size="small"
          defaultValue={u.description || ''}
          placeholder="点击输入管理员标签"
          maxLength={500}
          onBlur={(e: FocusEvent) => {
            const val = (e.target as HTMLInputElement).value.trim()
            void saveRemark(u, val)
          }}
        />
      )
    },
  },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  {
    title: '操作',
    width: 320,
    align: 'center',
    fixed: fixed.value,
    render: ({ record }) => {
      const u = record as T.ListItem
      const muted = isMuted(u)
      const isForumAdmin = !!u.forumAdmin
      const isSelf = String(u.id) === String(adminUserStore.userInfo.id)
      return (
        <Space wrap size={4}>
          <GiButton
            type="edit"
            size="mini"
            disabled={u.disabled}
            onClick={() => onEdit(u)}
          />
          {!isSelf ? (
            <GiButton
              size="mini"
              status={muted ? 'success' : 'warning'}
              disabled={u.disabled}
              onClick={() => confirmMute(u)}
            >
              {muted ? '解禁' : '禁言'}
            </GiButton>
          ) : null}
          {!isSelf
            ? (isForumAdmin
                ? (
                    <GiButton
                      size="mini"
                      status="danger"
                      disabled={u.disabled}
                      onClick={() => confirmRemoveForumAdmin(u)}
                    >
                      解除管理员
                    </GiButton>
                  )
                : (
                    <GiButton
                      size="mini"
                      status="success"
                      disabled={u.disabled}
                      onClick={() => confirmSetForumAdmin(u)}
                    >
                      设置为管理员
                    </GiButton>
                  ))
            : null}
          {!isSelf ? (
            <Popconfirm type="warning" content="确定删除该用户吗?" onBeforeOk={() => onDelete(u)}>
              <GiButton type="delete" size="mini" disabled={u.disabled} />
            </Popconfirm>
          ) : null}
        </Space>
      )
    },
  },
]
</script>

<style lang="scss" scoped>
.user-table {
  :deep(.arco-table-th),
  :deep(.arco-table-td) {
    text-align: center;
  }

  :deep(.arco-table-cell) {
    justify-content: center;
  }
}
</style>
