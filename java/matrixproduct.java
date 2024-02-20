import java.util.Scanner;

public class matrixproduct{
    public static void main(String[] args) {
        int op = 1, lin=0, col=0, blockSize = 0;
        Scanner sc = new Scanner(System.in);

        do{
            System.out.println();
            System.out.println("1. Multiplication");
            System.out.println("2. Line Multiplication");
            System.out.println("3. Block Multiplication");
            System.out.println("4. Run all stats");
            System.out.printf("Selection?: ");
            op = sc.nextInt();

            if (op == 0 ) break;

            if(op != 4){
            System.out.printf("Dimensions: lins=cols ? ");
            lin = sc.nextInt();
            col = lin;
            }

            switch (op) {
                case 1:
                    OnMult(lin, col);
                    break;
                case 2:
				    OnMultLine(lin, col);
                    break;
                case 3:
                    OnMultBlock(lin, col, blockSize);
                case 4:
                    runStats();  
                default:
                    break;
            }

        } while(op != 0);
    }

    public static void OnMult(int m_ar, int m_br){
        double temp;

        double pha[] = new double[m_ar * m_br];
        double phb[] = new double[m_ar * m_br];
        double phc[] = new double[m_ar * m_br];

        for(int i=0; i<m_ar; i++)
            for(int j=0; j<m_ar; j++)
                pha[i*m_ar + j] = (double)1.0;



        for(int i=0; i<m_br; i++)
            for(int j=0; j<m_br; j++)
                phb[i*m_br + j] = (double)(i+1);

        long time1 = System.nanoTime();

        for(int i = 0; i < m_ar; i++){
            for(int j = 0; j < m_br; j++){
                temp = 0;
                for( int k = 0; k < m_ar; k++){
                    temp += pha[i * m_ar + k] * phb[k * m_br +j];
                }
                phc[i * m_ar + j] = temp;
            }
        }
        long time2 = System.nanoTime();
        long duration = time2 - time1;

        double seconds = (double)duration / 1000000000.0;

        System.out.printf("Time: %3.3f seconds\n",seconds);


        System.out.println("Result matrix: ");
     
        for (int i = 0; i < 1; i++) {
            for (int j = 0; j < Math.min(10, m_br); j++) {
                System.out.print((int)phc[i*m_ar+j] + " ");
            }
        }
    }
    
    public static void OnMultLine(int m_ar, int m_br){
        System.out.println("todo");
    }

    public static void OnMultBlock(int m_ar, int m_br, int blockSize){
        System.out.println("todo");
    }

    public static void runStats(){
        System.out.println("------Line Multiplication------");

        for (int n = 600; n <= 3000; n+=400) {
            System.out.printf("n=%d\n", n);
            OnMult( n, n); 
            System.out.println("----\n");    
        }
    }
}   