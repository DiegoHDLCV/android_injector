import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { AuthProvider } from '@/hooks/useAuth';
import { NotificationProvider, NotificationContainer } from '@/hooks/useNotification';
import { POSConnectionProvider } from '@/hooks/usePOSConnection';
import Navbar from '@/components/Navbar';

const inter = Inter({ 
  subsets: ["latin"],
  variable: "--font-inter",
});

export const metadata: Metadata = {
  title: "Injector System",
  description: "Sistema de inyección de llaves criptográficas",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  console.log('[LAYOUT] 🔄 Layout principal renderizado');
  
  return (
    <html lang="es" className="dark">
      <body
        className={`${inter.variable} font-sans antialiased bg-gray-900 text-white min-h-screen`}
      >
        <NotificationProvider>
          <AuthProvider>
            <POSConnectionProvider>
              <div className="min-h-screen">
                <Navbar />
                <main className="pt-16">
                  {children}
                </main>
              </div>
              <NotificationContainer />
            </POSConnectionProvider>
          </AuthProvider>
        </NotificationProvider>
      </body>
    </html>
  );
}
