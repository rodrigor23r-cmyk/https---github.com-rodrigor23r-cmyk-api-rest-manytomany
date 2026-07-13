package com.example.entities;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "tutorials")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class Tutorial implements Serializable {
    /*
     * de parte de Jerónimo:
     * 
     * @EntityGraph(attributePaths = {"tags"}) //Spring Data JPA genera una consulta
     * con un JOIN para traer los tags evitando consulta N+1
     * List<Tutorial> findTutorialsByTagsId(Long tagId);
     */

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String title;
    private String description;
    private boolean published;

    //@Builder.Default // para evitar que lo inicialice
    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "tutorials_tags", joinColumns = { @JoinColumn(name = "tutorial_id") }, inverseJoinColumns = {
            @JoinColumn(name = "tag_id") }) // esta línea es redundante
    private Set<Tag> tags; // aquí he quitado = new HashSet<>();


    public void addTag(Tag tag) {

        if (this.tags == null) {
            this.tags = new HashSet<>();
        }

        if (tag.getTutorials() == null) {
            tag.setTutorials(new HashSet<>());
        }

        this.tags.add(tag);
        tag.getTutorials().add(this);
    }

    public void removeTag(long tagId) {
        Tag tag = this.tags.stream().filter(t -> t.getId() == tagId).findFirst().orElse(null);
        if (tag != null) {
            this.tags.remove(tag);
            tag.getTutorials().remove(this);
        }
    }

}
