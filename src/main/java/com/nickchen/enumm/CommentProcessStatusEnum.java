package com.nickchen.enumm;

/**
 * 阅读进度的状态枚举
 */
public enum CommentProcessStatusEnum {
    TODO("下一步要看", 0), DOING("阅读中", 1), ALMOST("还有部分", 2), FINISH("完成", 3);

    public static final String TAG = "$";
    private String desc;
    private int status;

    CommentProcessStatusEnum(String desc, int status) {
        this.desc = desc;
        this.status = status;
    }

    public static CommentProcessStatusEnum getByStatus(int status) {
        for (CommentProcessStatusEnum e : CommentProcessStatusEnum.values()) {
            if (e.status == status) {
                return e;
            }
        }
        return null;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}