class TestDrive {

    public static void main(String[] args) {
        Laptop laptop;

        LaptopFactory dellFactory = /* write your code here */
        LaptopFactory macbookFactory = /* write your code here */

        System.out.println("-Hello, I need a Windows laptop");
        System.out.println("-Okay! Please wait a sec, - Calling to the DellXpsFactory. " +
                "-Bring me a Dell laptop");

        laptop = /* write your code here */
        System.out.println(laptop.getDescription());
        System.out.println("There it is!\n");

        System.out.println("-Hello, I need a macOS laptop");
        System.out.println("-Okay! Please wait a sec, - Calling to the MacBookFactory. " +
                "-Bring me a MacBook laptop");

        laptop = /* write your code here */
        System.out.println(laptop.getDescription());
        System.out.println("There it is!");
    }
}

interface LaptopFactory {
    Laptop createComputer();
}

class MacBookFactory implements LaptopFactory {

    @Override
    public Laptop createComputer() {
        LaptopDetailsFactory detailsFactory = new MacBookDetailsFactory();

        return new MacBook(detailsFactory);
    }
}

class DellXpsFactory implements LaptopFactory {

    @Override
    public Laptop createComputer() {
        LaptopDetailsFactory detailsFactory = new DellXpsDetailsFactory();

        return new DellXps(detailsFactory);
    }
}

interface LaptopDetailsFactory {

    Display createDisplay();

    GraphicCard createGraphicCard();

    Processor createProcessor();

    Ssd createSsd();
}

class MacBookDetailsFactory implements LaptopDetailsFactory {

    @Override
    public Display createDisplay() {
        return new MacBookDisplay();
    }

    @Override
    public GraphicCard createGraphicCard() {
        return new MacBookGraphicCard();
    }

    @Override
    public Processor createProcessor() {
        return new MacBookProcessor();
    }

    @Override
    public Ssd createSsd() {
        return new MacBookSsd();
    }
}

class DellXpsDetailsFactory implements LaptopDetailsFactory {

    @Override
    public Display createDisplay() {
        return new DellXpsDisplay();
    }

    @Override
    public GraphicCard createGraphicCard() {
        return new DellXpsGraphicCard();
    }

    @Override
    public Processor createProcessor() {
        return new DellXpsProcessor();
    }

    @Override
    public Ssd createSsd() {
        return new DellXpsSsd();
    }
}

abstract class Laptop {
    Display display;
    GraphicCard graphicCard;
    Processor processor;
    Ssd ssd;

    abstract String getDescription();

    @Override
    public String toString() {
        return "Display: " + display.toString() + "\n"
                + "GraphicCard: " + graphicCard.toString() + "\n"
                + "Processor: " + processor.toString() + "\n"
                + "SSD: " + ssd.toString();
    }
}

class MacBook extends Laptop {

    MacBook(LaptopDetailsFactory detailsFactory) {
        super();
        display = detailsFactory.createDisplay();
        graphicCard = detailsFactory.createGraphicCard();
        processor = detailsFactory.createProcessor();
        ssd = detailsFactory.createSsd();
    }

    @Override
    public String getDescription() {
        return "This is a MacBook Pro 13\"\n" + super.toString();
    }
}

class DellXps extends Laptop {

    DellXps(LaptopDetailsFactory detailsFactory) {
        super();
        display = detailsFactory.createDisplay();
        graphicCard = detailsFactory.createGraphicCard();
        processor = detailsFactory.createProcessor();
        ssd = detailsFactory.createSsd();
    }

    @Override
    public String getDescription() {
        return "This is a Dell XPS 9370\n" + super.toString();
    }
}

interface Display {
    String toString();
}

class MacBookDisplay implements Display {

    @Override
    public String toString() {
        return "13\" 4K display";
    }
}

class DellXpsDisplay implements Display {
    @Override
    public String toString() {
        return "13\" FHD screen";
    }
}

interface GraphicCard {
    String toString();
}

class MacBookGraphicCard implements GraphicCard {
    @Override
    public String toString() {
        return "Intel Iris Plus Graphics 640";
    }
}

class DellXpsGraphicCard implements GraphicCard {
    @Override
    public String toString() {
        return "Intel UHD 620 GPU";
    }
}

interface Processor {
    String toString();
}

class MacBookProcessor implements Processor {

    @Override
    public String toString() {
        return "Dual-Core i5";
    }
}

class DellXpsProcessor implements Processor {
    @Override
    public String toString() {
        return "Core i7";
    }
}

interface Ssd {
    String toString();
}

class MacBookSsd implements Ssd {
    @Override
    public String toString() {
        return "256Gb SSD";
    }
}

class DellXpsSsd implements Ssd {
    @Override
    public String toString() {
        return "256Gb SSD";
    }
}