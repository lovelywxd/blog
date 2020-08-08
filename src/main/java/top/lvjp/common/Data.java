package top.lvjp.common;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author lvjp
 * @date 2020/8/8
 */
public class Data implements Serializable {
    private static final long serialVersionUID = 6007399410819407016L;

    private Integer id;
    private String value;

    public Data() {
    }

    public Data(Integer id, String value) {
        this.id = id;
        this.value = value;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "Data{" +
                "id=" + id +
                ", value='" + value + '\'' +
                '}';
    }
}
