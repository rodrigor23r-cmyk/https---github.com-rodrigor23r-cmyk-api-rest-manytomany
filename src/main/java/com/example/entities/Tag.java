package com.example.entities;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "tags")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // solo lo usa @Builder; así Jackson usa el constructor vacío y el Set nunca es null
@Getter
@Setter
@ToString
@Builder
public class Tag implements Serializable {

    private static final long serialVersionUID = 1L;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @NotBlank(message = "El nombre es obligatorio") // F7: un tag no puede guardarse sin nombre (PUT con @Valid -> 400; POST lo comprueba TagServiceImpl)
    private String name;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE}, mappedBy = "tags")
    @JsonIgnore
    @ToString.Exclude // Evita la recursión infinita al imprimir el objeto Tag
    @Setter(AccessLevel.NONE) // el Set solo se modifica con Tutorial.addTag()/removeTag(), nunca se sustituye
    private Set<Tutorial> tutorials = new HashSet<>();
}

/**
 * 
 * DTO

https://www.google.es/search?q=dto+java+que+es&oq=dto+java+&aqs=chrome.2.69i57j0l5.18657j0j8&sourceid=chrome&ie=UTF-8

Con hibernate y JPA

https://vladmihalcea.com/the-best-way-to-map-a-projection-query-to-a-dto-with-jpa-and-hibernate/


https://www.youtube.com/watch?v=gBaCpsqsWfY

 */