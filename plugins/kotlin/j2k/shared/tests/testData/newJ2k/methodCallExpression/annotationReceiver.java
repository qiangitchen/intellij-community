// IGNORE_K2
@interface Ann {
    String foobar();
}

class C {
    void process(Ann annotation) {
        System.out.println(annotation.toString());
        System.out.println(annotation.foobar());
    }
}